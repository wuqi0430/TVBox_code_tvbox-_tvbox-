package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.http.HttpDoRequest;
import com.lcstudio.commonsurport.util.DateUtil;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirstFragment extends BaseLazyFragment implements View.OnClickListener {

    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    private HomeHotVodAdapter mAdapter;
    private List<Movie.Video> mRecommendVideos;
    private List<Movie.Video> mDobanVideos = new ArrayList<>();
    private int mStart = 40;
    private TvRecyclerView mHotGridView;
    private ExecutorService mExecutorService;

    public static FirstFragment newInstance(List<Movie.Video> recVod) {
        return new FirstFragment().setmRecommendVideos(recVod);
    }

    public FirstFragment setmRecommendVideos(List<Movie.Video> recVod) {
        this.mRecommendVideos = recVod;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home;
    }

    @Override
    protected void init() {
        mExecutorService =  Executors.newSingleThreadExecutor();
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.shareLayout);
        mHotGridView = findViewById(R.id.tvHotList);

        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);

        initGridView();
        setLoadSir(mHotGridView);
        getVideos();
    }

    private void initGridView() {
        mHotGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 5 : 6));
        mAdapter = new HomeHotVodAdapter();
        mAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (ApiConfig.getInstance().getSiteBeanList().isEmpty()) {
                Toast.makeText(mContext, "配置文件获取中，请稍后", Toast.LENGTH_SHORT).show();
                return;
            }
            Movie.Video vod = ((Movie.Video) adapter.getItem(position));
            if (vod.isMore) {
                //点击查看更多电影
                getMoreVideos(mStart);
                mStart += 20;
                return;
            }
            if (vod.id != null && !vod.id.isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString("id", vod.id);
                bundle.putString("sourceKey", vod.sourceKey);
                jumpActivity(DetailActivity.class, bundle);
            } else {
                Intent newIntent = new Intent(mContext, SearchActivity.class);
                newIntent.putExtra("title", vod.name);
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mActivity.startActivity(newIntent);
            }
        });

        mAdapter.setOnLoadMoreListener(() -> {
            //滑到底触发
            mAdapter.setEnableLoadMore(true);
            getMoreVideos(mStart);
            mStart += 20;
        }, mHotGridView);
        mAdapter.setLoadMoreView(new LoadMoreView());

        mHotGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        mHotGridView.setAdapter(mAdapter);
    }

    private void getVideos() {
        int homeSource = Hawk.get(HawkConfig.HOME_REC, 0);
        if (homeSource == 1) {
            //网站推荐
            if (mRecommendVideos != null) {
                mAdapter.setNewData(mRecommendVideos);
            }
            showSuccess();
        } else if (homeSource == 2) {
            //历史
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) {
                    vod.note = "上次看到" + vodInfo.playNote;
                }
                vodList.add(vod);
            }
            mAdapter.setNewData(vodList);
            showSuccess();
        } else {
            //豆瓣推荐
            showLoading();
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    int year = DateUtil.getLastNYear(0);
                    String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + 0;
                    String result = HttpDoRequest.getInstance(getContext()).doGetRequestInOkGo(doubanUrl, null, true, 5 * 60 * 1000);
                    List<Movie.Video> list0 = parseJson(result);
                    if (!NullUtil.isNull(list0)) {
                        mDobanVideos.addAll(list0);
                    }
                    mActivity.runOnUiThread(() -> {
                        showSuccess();
                        mAdapter.setNewData(mDobanVideos);
                        getMoreVideos(20);
                    });
                }
            }.start();
        }
    }

    private void getMoreVideos(int start) {
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                int year = DateUtil.getLastNYear(0);
                String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + start;
                String result = HttpDoRequest.getInstance(getContext()).doGetRequestInOkGo(doubanUrl, null, true, 5 * 60 * 1000);
                List<Movie.Video> listMore = parseJson(result);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //UiHelper.showToast(getActivity(), doubanUrl + " result=" + result);
                        L.d(doubanUrl + " result=" + result);
                        if (!NullUtil.isNull(listMore)) {
                            mAdapter.addData(listMore);//解决焦点跳走的问题
                        }
                        mAdapter.loadMoreComplete();
                    }
                });
            }
        });
    }

    private ArrayList<Movie.Video> parseJson(String json) {
        ArrayList<Movie.Video> videos = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString();
                videos.add(vod);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        return videos;
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }
        }
    };

    @Override
    public void onClick(View v) {
        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.shareLayout) {
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        }
    }
}