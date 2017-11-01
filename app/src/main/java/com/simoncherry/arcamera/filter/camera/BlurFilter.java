package com.simoncherry.arcamera.filter.camera;

import android.content.res.Resources;

/**
 * Created by Simon on 2017/7/6.
 */

public class BlurFilter extends AFilter {

    public BlurFilter(Resources mRes) {
        super(mRes);
    }

    @Override
    protected void onCreate() {
        createProgramByAssetsFile("shader/base_vertex.sh",
                "shader/color/blur_fragment.frag");
    }

    @Override
    protected void onSizeChanged(int width, int height) {
    }
}
