package com.github.tvbox.osc.callback;

import com.kingja.loadsir.callback.Callback;
import com.studio.osc.R;

/**
 * @author pj567
 * @date :2020/12/24
 * @description:
 */
public class EmptyCallback extends Callback {
    @Override
    protected int onCreateView() {
        return R.layout.loadsir_empty_layout;
    }
}