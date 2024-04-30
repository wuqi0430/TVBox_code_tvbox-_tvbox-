package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.fragment.FirstFragment;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.MarqueeTextView;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lcstudio.commonsurport.util.StringUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;
import com.uisupport.update.HttpUtil;
import com.uisupport.update.ReportConfig;
import com.uisupport.update.RspReportConfig;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * tvbox交流QQ群：879811030
 */
public class HomeActivity extends BaseActivity implements View.OnClickListener {
    private TextView tvDate;
    private SourceViewModel sourceViewModel;
    private TvRecyclerView mMovieSortRV;
    private SortAdapter sortAdapter;
    private NoScrollViewPager mContentViewPager;
    private HomePageAdapter mPageAdapter;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean bDownOrUp = false;
    private boolean bSortChange = false;
    private int mCurrentSelected = 0;
    private int mSortFocused = 0;
    public View mSortFocusView = null;
    private Handler mHandler;
    private long mExitTime = 0;
    private MarqueeTextView mCautionTv;
    private SPDataUtil mSpDataUtil;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    private void initView() {
        this.tvDate = findViewById(R.id.tvDate);
        mCautionTv = findViewById(R.id.caution_tv);
        findViewById(R.id.layout_search2).setOnClickListener(this);
        findViewById(R.id.layoutSearch).setOnClickListener(this);
        findViewById(R.id.layout_history).setOnClickListener(this);
        findViewById(R.id.layout_collect).setOnClickListener(this);
        findViewById(R.id.layout_share).setOnClickListener(this);
        findViewById(R.id.layout_setting).setOnClickListener(this);
        this.mMovieSortRV = findViewById(R.id.sortRV);
        this.mContentViewPager = findViewById(R.id.viewPager);
    }

    @Override
    protected void init() {
        // 初始化Web服务器
        ControlManager.get().startServer(getApplicationContext());
        mHandler = new Handler(Looper.getMainLooper());
        mSpDataUtil = new SPDataUtil(getBaseContext());
        EventBus.getDefault().register(this);
        initView();
        initSortView();
        initFirstPager();
        observePages();
        initSiteFile();
        showTime();
        getConfig(this);
        ReportConfig config = (ReportConfig) mSpDataUtil.getObjectValue(UiConstans.PRE_KEY_CONFIG);
        if (config != null && config.getCaution() != null) {
            mCautionTv.setText(config.getCaution());
        }

    }

    public void getConfig(Context context) {
        new Thread() {
            @Override
            public void run() {
                RspReportConfig bean = HttpUtil.getConfig(UiConstans.GET_CONFIG_URL, context);
                if (null != bean && null != bean.resultData) {
                    SPDataUtil spDataUtil = new SPDataUtil(context);
                    spDataUtil.saveObjectValue(UiConstans.PRE_KEY_CONFIG, bean.resultData);
                }
            }
        }.start();
    }

    private void initSortView(){
        this.sortAdapter = new SortAdapter();
        this.mMovieSortRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mMovieSortRV.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mMovieSortRV.setAdapter(this.sortAdapter);
        this.mMovieSortRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.bDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(false);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                    textView.invalidate();
                    view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    changeTap(view, position);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && mCurrentSelected == position && !sortAdapter.getItem(position).filters.isEmpty()) { // 弹出筛选
                    BaseLazyFragment baseLazyFragment = fragments.get(mCurrentSelected);
                    if ((baseLazyFragment instanceof GridFragment)) {
                        ((GridFragment) baseLazyFragment).showFilter();
                    }
                }
            }
        });
    }

    private void initFirstPager() {
        fragments.add(FirstFragment.newInstance(null));
        mPageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
        mContentViewPager.setPageTransformer(true, new DefaultTransformer());
        mContentViewPager.setAdapter(mPageAdapter);
        mContentViewPager.setCurrentItem(0, true);
        List<MovieSort.SortData> sortDataList = new ArrayList<>();
        sortDataList.add(new MovieSort.SortData("my0", "首页"));
        sortAdapter.setNewData(sortDataList);
        mMovieSortRV.setSelection(0);
    }

    private void showTime() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                tvDate.setText(StringUtil.getFormatTime_HH_MM(System.currentTimeMillis()));
                mHandler.postDelayed(this, 1000);
            }
        });
    }

    private void changeTap(View view, int position) {
        bDownOrUp = false;
        bSortChange = true;
        view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
        TextView textView = view.findViewById(R.id.tvTitle);
        textView.getPaint().setFakeBoldText(true);
        textView.setTextColor(getResources().getColor(R.color.color_FFFFFF));
        textView.invalidate();
        if (sortAdapter.getItem(position) != null && !NullUtil.isNull(sortAdapter.getItem(position).filters)) {
            view.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
        }
        mSortFocusView = view;
        mSortFocused = position;
        mHandler.removeCallbacks(mChangeTapRunnable);
        mHandler.postDelayed(mChangeTapRunnable, 200);
    }

    private void observePages() {
        L.d("observePages()");
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                L.d(" sortResult success() " + new Gson().toJson(absXml));
                if (absXml != null && !NullUtil.isNull(absXml.classes.sortList)) {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.getInstance().getHomeBean().getKey(), absXml.classes.sortList, true));
                } else {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.getInstance().getHomeBean().getKey(), new ArrayList<>(), true));
                }
                freshViewPager(absXml);
                mMovieSortRV.setSelection(0);
            }
        });
    }

    //获取site.file配置
    private void initSiteFile(){
        L.d("initSiteFile() ");
        ApiConfig.getInstance().loadSitesFile(HomeActivity.this, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                L.d(" loadSitesFile success()");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initJar();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                initHomeBean();
                            }
                        }, 1000);
                    }
                });
            }
        });
    }

    private void initJar() {
        if (!NullUtil.isNull(ApiConfig.getInstance().getmSpider())) {
            ApiConfig.getInstance().loadJar(getBaseContext(), false, ApiConfig.getInstance().getmSpider(), new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    L.d(" initJar success()");
                }
            });
        }
    }

    private void initHomeBean() {
        ApiConfig.getInstance().initHomeBean(new ApiConfig.InitHomeCallback() {
            @Override
            public void success() {
                L.d(" initHomeBean success()");
                runOnUiThread(() -> {
                    //获取sort列表
                    sourceViewModel.getSort(ApiConfig.getInstance().getHomeBean().getKey());
                });
            }
        });
    }

    private void freshViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            fragments.clear();
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && !NullUtil.isNull(absXml.videoList)) {
                        //首页显示网站推荐
                        fragments.add(FirstFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(FirstFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            mPageAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onBackPressed() {
        int i;
        if (this.fragments.size() <= 0 || this.mSortFocused >= this.fragments.size() || (i = this.mSortFocused) < 0) {
            exit();
            return;
        }
        BaseLazyFragment baseLazyFragment = this.fragments.get(i);
        if (baseLazyFragment instanceof GridFragment || baseLazyFragment instanceof FirstFragment) {
            View view = this.mSortFocusView;
            if (view != null && !view.isFocused()) {
                this.mSortFocusView.requestFocus();
            } else if (this.mSortFocused != 0) {
                this.mMovieSortRV.setSelection(0);
            } else {
                exit();
            }
        } else {
            exit();
        }
    }

    private void exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.getInstance().getSiteBean("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(newIntent);
            }
        }
    }

    private Runnable mChangeTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (bSortChange) {
                bSortChange = false;
                if (mSortFocused != mCurrentSelected) {
                    mCurrentSelected = mSortFocused;
                    mContentViewPager.setCurrentItem(mSortFocused, false);
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
//        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        switch (view.getId()) {
            case R.id.layout_search2:
            case R.id.layoutSearch:
                intent = new Intent(mContext, SearchActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_history:
                intent = new Intent(mContext, HistoryActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_collect:
                intent = new Intent(mContext, CollectActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_share:
                //SystemUitl.share(HomeActivity.this, "分享", "我发现个很好用的APP，官网:" + UiConstans.GO_BROWSER_URL);
                jumpActivity(LivePlayActivity.class);
                break;
            case R.id.layout_setting:
                intent = new Intent(mContext, SettingActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

}