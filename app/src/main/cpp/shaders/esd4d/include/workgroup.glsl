//
// Created by eszdman on 20.11.2026.
//

#ifdef ESD4D_ROW_WIDTH
layout(local_size_x=ESD4D_ROW_WIDTH, local_size_y=1) in;
#else
layout(local_size_x=8, local_size_y=8) in;
#endif
