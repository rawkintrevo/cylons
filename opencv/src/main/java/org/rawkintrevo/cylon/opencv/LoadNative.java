package org.rawkintrevo.cylon.opencv;

import org.opencv.core.Core;

public class LoadNative {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    native void loadNative();
}