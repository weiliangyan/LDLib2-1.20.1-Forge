package com.lowdragmc.lowdraglib2.compat.font.providers;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class FreeTypeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Object LIBRARY_LOCK = new Object();
    private static long library = 0L;

    public FreeTypeUtil() {
    }

    public static long getLibrary() {
        synchronized(LIBRARY_LOCK) {
            if (library == 0L) {
                MemoryStack memorystack = MemoryStack.stackPush();

                try {
                    PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
                    assertError(FreeType.FT_Init_FreeType(pointerbuffer), "Initializing FreeType library");
                    library = pointerbuffer.get();
                } catch (Throwable var6) {
                    if (memorystack != null) {
                        try {
                            memorystack.close();
                        } catch (Throwable var5) {
                            var6.addSuppressed(var5);
                        }
                    }

                    throw var6;
                }

                if (memorystack != null) {
                    memorystack.close();
                }
            }

            return library;
        }
    }

    public static void assertError(int errorId, String action) {
        if (errorId != 0) {
            String var10002 = describeError(errorId);
            throw new IllegalStateException("FreeType error: " + var10002 + " (" + action + ")");
        }
    }

    public static boolean checkError(int errorId, String action) {
        if (errorId != 0) {
            LOGGER.error("FreeType error: {} ({})", describeError(errorId), action);
            return true;
        } else {
            return false;
        }
    }

    private static String describeError(int errorId) {
        String s = FreeType.FT_Error_String(errorId);
        return s != null ? s : "Unrecognized error: 0x" + Integer.toHexString(errorId);
    }

    public static FT_Vector setVector(FT_Vector vector, float x, float y) {
        long i = (long)Math.round(x * 64.0F);
        long j = (long)Math.round(y * 64.0F);
        return vector.set(i, j);
    }

    public static float x(FT_Vector vector) {
        return (float)vector.x() / 64.0F;
    }

    public static void destroy() {
        synchronized(LIBRARY_LOCK) {
            if (library != 0L) {
                FreeType.FT_Done_Library(library);
                library = 0L;
            }

        }
    }
}
