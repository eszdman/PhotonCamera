package com.particlesdevs.photoncamera.gallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.widget.ViewPager2;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.gallery.adapters.ImageAdapter;
import com.particlesdevs.photoncamera.gallery.binding.CustomBinding;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.helper.GalleryShare;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.ui.GalleryActivity;
import com.particlesdevs.photoncamera.util.BurstArchiveStore;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class BurstArchiveGalleryTest {
    @Test
    public void savesInRawDiscoversAndSharesArchiveWithoutPreview() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assumeTrue("Existing DCIM folder grant required", SimpleStorageHelper.hasStorageAccess(context));
        String stem = "ZIP_TEST_" + UUID.randomUUID();
        byte[][] frames = {{73, 73, 42, 0, 1}, {73, 73, 42, 0, 2}};
        DocumentFile archive = BurstArchiveStore.save(context, stem, 2,
                (index, output) -> output.write(frames[index]));
        try {
            assertEquals(stem + "_burst.zip", archive.getName());
            assertTrue(archive.getUri().toString().contains("Raw"));
            try (ZipInputStream zip = new ZipInputStream(context.getContentResolver().openInputStream(archive.getUri()))) {
                for (int i = 0; i < 2; i++) {
                    assertEquals(stem + "_f0" + i + ".dng", zip.getNextEntry().getName());
                    ByteArrayOutputStream decoded = new ByteArrayOutputStream();
                    byte[] chunk = new byte[1024];
                    int count;
                    while ((count = zip.read(chunk)) != -1) decoded.write(chunk, 0, count);
                    assertArrayEquals(frames[i], decoded.toByteArray());
                }
                assertNull(zip.getNextEntry());
            }
            ImageFile file = GalleryFileOperations.FindAllFoldersWithImages(context.getContentResolver()).stream()
                    .flatMap(folder -> folder.getAllImageFiles().stream())
                    .filter(candidate -> (stem + "_burst.zip").equals(candidate.getDisplayName()))
                    .findFirst().orElseThrow(() -> new AssertionError("ZIP missing from gallery"));
            assertTrue(file.isZip());
            assertTrue(file.getId() < 0);
            Intent share = GalleryShare.createIntent(Collections.singletonList(file));
            assertEquals("application/zip", share.getType());
            assertEquals(Intent.ACTION_SEND, share.getAction());
            assertNotEquals(0, share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            assertEquals(file.getFileUri(), share.getClipData().getItemAt(0).getUri());
            // Read the exact URI handed to receiving apps.
            try (java.io.InputStream input = context.getContentResolver().openInputStream(file.getFileUri())) {
                assertEquals('P', input.read());
                assertEquals('K', input.read());
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                GalleryItem item = new GalleryItem(file);
                ImageAdapter adapter = new ImageAdapter(Collections.singletonList(item));
                ImageAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(context), adapter.getItemViewType(0));
                adapter.onBindViewHolder(holder, 0);
                adapter.onViewAttachedToWindow(holder);
                assertNull(holder.getSsiv());
                assertNull(adapter.getActiveSsiv(0));
                TextView description = holder.itemView.findViewById(R.id.archive_description);
                assertTrue(description.getText().toString().contains(file.getDisplayName()));
                assertNotNull(description.getCompoundDrawables()[1]);
                adapter.onViewDetachedFromWindow(holder);
                adapter.onViewAttachedToWindow(holder);
                adapter.onViewDetachedFromWindow(holder);
                adapter.onViewRecycled(holder);
                ImageView thumbnail = new ImageView(context);
                CustomBinding.loadImage(thumbnail, item);
                assertNotNull(thumbnail.getDrawable());
                assertEquals(ImageView.ScaleType.CENTER_INSIDE, thumbnail.getScaleType());
                CustomBinding.clearImage(thumbnail);
            });
            // Exercise real RecyclerView attachment during gallery launch, not
            // just manual binding of a holder that never enters a window.
            Intent openArchive = new Intent(context, GalleryActivity.class)
                    .setAction(Intent.ACTION_VIEW).setData(file.getFileUri());
            try (ActivityScenario<GalleryActivity> scenario = ActivityScenario.launch(openArchive)) {
                scenario.onActivity(activity -> {
                    ViewPager2 pager = activity.findViewById(R.id.view_pager);
                    ImageAdapter adapter = (ImageAdapter) pager.getAdapter();
                    assertNotNull(adapter);
                    assertEquals(1, adapter.getItemViewType(pager.getCurrentItem()));
                    assertNull(adapter.getActiveSsiv(pager.getCurrentItem()));
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                scenario.moveToState(Lifecycle.State.RESUMED);
            }
        } finally {
            assertTrue(archive.delete());
        }
    }

    @Test
    public void failedExportLeavesNoArchiveOrPendingFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assumeTrue("Existing DCIM folder grant required", SimpleStorageHelper.hasStorageAccess(context));
        DocumentFile folder = BurstArchiveStore.getRawFolder(context, true);
        int before = folder.listFiles().length;
        try {
            BurstArchiveStore.save(context, "ZIP_TEST_FAIL_" + UUID.randomUUID(), 2, (index, output) -> {
                throw new IOException("Simulated write failure");
            });
            fail("Expected failed export");
        } catch (IOException expected) {
            assertEquals("Simulated write failure", expected.getMessage());
        }
        assertEquals(before, folder.listFiles().length);
    }

    @Test
    public void sharesZipSelectionsAndMixedSelectionsWithReadableClipUris() {
        ImageFile zip = new ImageFile(-1, Uri.parse("content://test/archive.zip"), "archive.ZIP", 1, 1, "");
        ImageFile zip2 = new ImageFile(-2, Uri.parse("content://test/archive2.zip"), "archive2.zip", 1, 1, "");
        ImageFile jpeg = new ImageFile(1, Uri.parse("content://test/photo.jpg"), "photo.jpg", 1, 1, "");
        Intent archives = GalleryShare.createIntent(Arrays.asList(zip, zip2));
        assertEquals("application/zip", archives.getType());
        assertEquals(Intent.ACTION_SEND_MULTIPLE, archives.getAction());
        Intent mixed = GalleryShare.createIntent(Arrays.asList(jpeg, zip));
        assertEquals("*/*", mixed.getType());
        assertEquals(2, mixed.getClipData().getItemCount());
        assertEquals(zip.getFileUri(), mixed.getClipData().getItemAt(1).getUri());
        assertNotEquals(0, mixed.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
