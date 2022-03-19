package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class GLImage implements AutoCloseable {
    private static String TAG = "GLImage";
    public Point size;
    public ByteBuffer byteBuffer;
    //Use carefully
    public Bitmap mBmp;
    GLFormat glFormat;

    public GLImage(Bitmap image){
        this.size = new Point(image.getWidth(),image.getHeight());
        glFormat = new GLFormat(GLFormat.DataType.SIMPLE_8,image.getByteCount()/(image.getWidth()*image.getHeight()));
        byteBuffer = getByteBuffer(image);
        mBmp = image;
    }
    public GLImage(File inputFile) {
        Bitmap image = BitmapFactory.decodeFile(inputFile.getAbsolutePath());
        this.size = new Point(image.getWidth(),image.getHeight());
        glFormat = new GLFormat(GLFormat.DataType.SIMPLE_8,image.getByteCount()/(image.getWidth()*image.getHeight()));
        byteBuffer = getByteBuffer(image);
    }
    public GLImage(InputStream inputFile) {
        Bitmap image = BitmapFactory.decodeStream(inputFile);
        this.size = new Point(image.getWidth(),image.getHeight());
        glFormat = new GLFormat(GLFormat.DataType.SIMPLE_8,image.getByteCount()/(image.getWidth()*image.getHeight()));
        byteBuffer = getByteBuffer(image);
    }

    public GLImage(Point size, GLFormat glFormat) {
        this.size = new Point(size);
        this.glFormat = new GLFormat(glFormat);
        byteBuffer = ByteBuffer.allocateDirect(size.x*size.y*glFormat.mChannels*glFormat.mFormat.mSize);
    }

    public GLImage(Point size, GLFormat glFormat, ByteBuffer byteBuffer) {
        this.size = new Point(size);
        this.glFormat = new GLFormat(glFormat);
        this.byteBuffer = byteBuffer;
    }

    private ByteBuffer getByteBuffer(Bitmap bufferedImage){
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferedImage.getByteCount());
        bufferedImage.copyPixelsToBuffer(byteBuffer);
        byteBuffer.position(0);
        return byteBuffer;
    }
    public Bitmap getBufferedImage(){
        return getBufferedImage(4);
    }
    public Bitmap getBufferedImage(int channels){
        byteBuffer.position(0);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, channels);
        Bitmap preview = Bitmap.createBitmap(size.x, size.y, bitmapF.getBufferedImageConfig());
        preview.copyPixelsFromBuffer(byteBuffer);
        return preview;
    }

    public Bitmap save(File output){
        FileOutputStream fOut = null;
        try {
            output.createNewFile();
            fOut = new FileOutputStream(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bmp = getBufferedImage();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fOut);
        return bmp;
    }


    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     *
     * <p>While this interface method is declared to throw {@code
     * Exception}, implementers are <em>strongly</em> encouraged to
     * declare concrete implementations of the {@code close} method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     *
     * <p> Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally <em>mark</em> the
     * resource as closed, prior to throwing the exception. The {@code
     * close} method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     *
     * <p><em>Implementers of this interface are also strongly advised
     * to not have the {@code close} method throw {@link
     * InterruptedException}.</em>
     * <p>
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an {@code
     * InterruptedException} is {@linkplain Throwable#addSuppressed
     * suppressed}.
     * <p>
     * More generally, if it would cause problems for an
     * exception to be suppressed, the {@code AutoCloseable.close}
     * method should not throw it.
     *
     * <p>Note that unlike the {@link Closeable#close close}
     * method of {@link Closeable}, this {@code close} method
     * is <em>not</em> required to be idempotent.  In other words,
     * calling this {@code close} method more than once may have some
     * visible side effect, unlike {@code Closeable.close} which is
     * required to have no effect if called more than once.
     * <p>
     * However, implementers of this interface are strongly encouraged
     * to make their {@code close} methods idempotent.
     */
    @Override
    public void close() {
        byteBuffer.clear();
        if(mBmp != null)
            mBmp.recycle();
    }
}
