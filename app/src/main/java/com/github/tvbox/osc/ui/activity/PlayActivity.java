package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.PlayBean;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.XWalkUtils;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.StringUtil;
import com.lcstudio.commonsurport.util.UIUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;
import com.uisupport.UiConstans;
import com.uisupport.update.HotWords;
import com.uisupport.update.HttpUtil;
import com.uisupport.update.Initer;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import me.jessyan.autosize.AutoSize;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PrintUtil;

/**
 * tvbox交流QQ群：879811030
 */
public class PlayActivity extends BaseActivity  implements View.OnClickListener {
    private VideoView mVideoView;
    private TextView mTimeTV;
    private TextView mPlayLoadTipTV;
    private TextView mMovieNameTV;
    private ImageView mPlayErrImg;
    private ProgressBar mPlayLoadingPb;
    private VodController mController;
    private SourceViewModel sourceViewModel;
    private String parseFlagStr;
    private String mWebUrl;
    private Map<String, Boolean> loadedUrlsMap = new HashMap<>();
    private boolean m_bLoadFound = false;
    private ExecutorService mExecutorService;
    private VodInfo mVodInfo;
    private String mSourceKeyStr;
    private JSONObject mVodPlayerCfgJsonObject;
    private SiteBean mSiteBean;
    private int autoRetryCount = 0;
    private Handler mHandler;
    private PlayBean mPlayBean;
    private long firstTimes = System.currentTimeMillis();

    private XWalkView mXwalkWebView;
    private XWalkWebClient mX5WebClient;
    private WebView mSysWebView;
    private SysWebClient mSysWebClient;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        L.d("onConfigurationChanged()");
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
        }
    }

    @Override
    protected void init() {
        mPlayBean = new PlayBean();
        mHandler = new Handler(Looper.getMainLooper());
        mExecutorService = Executors.newSingleThreadExecutor();
        getIntentData();
        initView();
        showSystemTime();
        initModelObserver();
        initPlayerConfig();
        preGetPlayUrl();
        PrintUtil.printPlayErrorMap();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
            mSourceKeyStr = bundle.getString("sourceKey");
            mSiteBean = ApiConfig.getInstance().getSiteBean(mSourceKeyStr);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        L.d("onResume()");
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }

    private void initView() {
        mVideoView = findViewById(R.id.mVideoView);
        mTimeTV = findViewById(R.id.system_time_tv);
        mPlayLoadTipTV = findViewById(R.id.play_load_tip);
        mMovieNameTV = findViewById(R.id.movie_name_tv);
        mPlayLoadingPb = findViewById(R.id.play_loading);
        mPlayErrImg = findViewById(R.id.play_load_error);
        mMovieNameTV.setOnClickListener(this);
        addControl();
        addProcessMng();
    }

    private void initModelObserver() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        Initer.init(this);
        sourceViewModel.playResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject info) {
                L.d("observe onChanged():" + new Gson().toJson(info));
                if (info != null) {
                    mPlayBean = UiHelper.getPlayBean(info);
                    String url = null;
                    if (!NullUtil.isNull(mPlayBean.playUrl) && DefaultConfig.isVideoFormat2(mPlayBean.playUrl)) {
                        url = mPlayBean.playUrl;
                    } else {
                        if (!NullUtil.isNull(mPlayBean.url)) {
                            url = mPlayBean.url;
                        }
                    }
                    if (!NullUtil.isNull(url) && DefaultConfig.isVideoFormat2(url)) {
                        playVideo(url, mPlayBean.headersMap);
                    } else if (!NullUtil.isNull(url) && DefaultConfig.isVideoFormat(url)) {
                        playVideo(url, mPlayBean.headersMap);
                    } else {
                        boolean bUseJxList = (mPlayBean.playUrl.isEmpty() && ApiConfig.getInstance().getmVipFlagList().contains(mPlayBean.flag)) || mPlayBean.bNeedJx;
                        initParse(mPlayBean.flag, bUseJxList, mPlayBean.playUrl, mPlayBean.url);
                    }
                } else {
                    errorWithRetry("获取播放地址错误", true);
                }
            }
        });
    }

    private void initPlayerConfig() {
        try {
            mVodPlayerCfgJsonObject = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfgJsonObject = new JSONObject();
        }
        try {
            if (!mVodPlayerCfgJsonObject.has("pl")) {
                mVodPlayerCfgJsonObject.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 1));
            }
            if (!mVodPlayerCfgJsonObject.has("pr")) {
                mVodPlayerCfgJsonObject.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfgJsonObject.has("ijk")) {
                mVodPlayerCfgJsonObject.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfgJsonObject.has("sc")) {
                mVodPlayerCfgJsonObject.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfgJsonObject.has("sp")) {
                mVodPlayerCfgJsonObject.put("sp", 1.0f);
            }
            if (!mVodPlayerCfgJsonObject.has("st")) {
                mVodPlayerCfgJsonObject.put("st", 0);
            }
            if (!mVodPlayerCfgJsonObject.has("et")) {
                mVodPlayerCfgJsonObject.put("et", 0);
            }
        } catch (Throwable th) {
            L.e(th.getMessage());
        }
        mController.setPlayerConfig(mVodPlayerCfgJsonObject);
    }

    private void preGetPlayUrl() {
        L.d("preGetPlayUrl() start");
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        showTipsView("正在获取播放信息", true, false);
        String title = mVodInfo.name + " " + vs.name;
        mMovieNameTV.setText(title);
        mController.setTitle("");
        playVideo(null, null); //stopPlay

        boolean bSuccess = Thunder.playStartWithTvbox(vs.url, new Thunder.ThunderCallback() {
            @Override
            public void status(int code, String info) {
                L.d("Thunder.play status()  info = " + info);
                if (code < 0) {
                    showTipsView(info, false, true);
                } else {
                    showTipsView(info, true, false);
                }
            }

            @Override
            public void list(String playList) {
                L.d("Thunder.play list() ");
            }

            @Override
            public void play(String url) {
                L.d("Thunder.play play() ");
                playVideo(url, null);
            }
        });

        if (!bSuccess) {
            String progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex;
            sourceViewModel.getPlayUrl(mSourceKeyStr, mVodInfo.playFlag, progressKey, vs.url);
        }
    }

    private void addControl() {
        mController = new VodController(this);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setFocusable(true);
        mController.setFocusableInTouchMode(true);
        mController.setGestureEnabled(true);
        mController.setControlListener(new VodController.VodControlListener() {
            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = mPlayBean.progressKeyStr;
                PlayActivity.this.playNext();
                if (rmProgress && preProgressKey != null) {
                    CacheManager.delete(MD5.string2MD5(preProgressKey), 0);
                }
            }

            @Override
            public void playPre() {
                playPrevious();
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfgJsonObject.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfgJsonObject));
            }

            @Override
            public void replay() {
                autoRetryCount = 0;
                preGetPlayUrl();
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false);
            }
        });
        mVideoView.addControlView(mController);
        mController.requestFocus();
    }

    private void addProcessMng(){
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                CacheManager.save(MD5.string2MD5(url), progress);
            }
            @Override
            public long getSavedProgress(String url) {
                int st = 0;
                try {
                    st = mVodPlayerCfgJsonObject.getInt("st");
                } catch (JSONException e) {
                    L.e(e);
                }
                long skip = st * 1000;
                if (CacheManager.getCache(MD5.string2MD5(url)) == null) {
                    return skip;
                }
                long rec = (long) CacheManager.getCache(MD5.string2MD5(url));
                if (rec < skip) {
                    return skip;
                }
                return rec;
            }
        };
        mVideoView.setProgressManager(progressManager);
    }

    private void errorWithRetry(String err, boolean finish) {
        L.d("errorWithRetry() "+err);
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        String site = ApiConfig.getInstance().getSiteBean(mVodInfo.sourceKey).getName();
        PrintUtil.saveError(site + "|" + mVodInfo.name + vs.name, err);
        runOnUiThread(() -> {
            if (!doRetry()) {
                if (finish) {
                    Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    showTipsView(err, false, true);
                }
            }
        });
    }

    private boolean doRetry() {
        L.d("doRetry （）autoRetryCount= " + autoRetryCount);
        int parseSize = ApiConfig.getInstance().getmParseBeanList().size();
        if (autoRetryCount < parseSize) {
            autoRetryCount++;
            preGetPlayUrl();
            return true;
        } else {
            autoRetryCount = 0;
            //最后重试播放url
            String url = null;
            if (!NullUtil.isNull(mPlayBean.playUrl)) {
                url = mPlayBean.playUrl;
            }
            if (!NullUtil.isNull(mPlayBean.url)) {
                url = mPlayBean.url;
            }
            if (!NullUtil.isNull(url)) {
                initWebViewAndLoad(url);
            }
            return false;
        }
    }

    private void playVideo(final String url, HashMap<String, String> headers) {
        L.d("playVideo() stop if url==null, url=" + url);
        runOnUiThread(() -> {
            stopParse();
            VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
            String playTitle = mVodInfo.name + " " + vs.name;
            String site = ApiConfig.getInstance().getSiteBean(mVodInfo.sourceKey).getName();
            if (mVideoView != null) {
                //停止播放， 再开始
                mVideoView.release();
                String playUrl = url;
                if (!NullUtil.isNull(playUrl)) {
                    showTipsView("缓冲中...", true, false);
                    //修复鑫鑫影视播放地址错误问题
                    if (playUrl.contains("bf.hhuus.com")) {
                        playUrl = playUrl.replace("bf.hhuus.com", "play.hhuus.com");
                        L.d("after replace playUrl=" + playUrl);
                    }

                    try {
                        int playerType = mVodPlayerCfgJsonObject.getInt("pl");
                        L.d("playerType=" + playerType);
                        if (playerType >= 10) {
                            showTipsView("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                            boolean bSuccess = false;
                            switch (playerType) {
                                case 10: {
                                    bSuccess = MXPlayer.run(PlayActivity.this, playUrl, playTitle, mPlayBean.playSubtitleStr, headers);
                                    break;
                                }
                                case 11: {
                                    bSuccess = ReexPlayer.run(PlayActivity.this, playUrl, playTitle, mPlayBean.playSubtitleStr, headers);
                                    break;
                                }
                            }
                            showTipsView("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (bSuccess ? "成功" : "失败"), bSuccess, !bSuccess);
                            insertVod(mSourceKeyStr, mVodInfo);
                            return;
                        }
                    } catch (JSONException e) {
                        L.e("playVideo " + e.getMessage());
                    }
                    L.d("调用自带播放器播放");
                    PlayerHelper.updateCfg(mVideoView, mVodPlayerCfgJsonObject);
                    mVideoView.setProgressKey(mPlayBean.progressKeyStr);
                    if (headers != null) {
                        mVideoView.setUrl(playUrl, headers);
                    } else {
                        mVideoView.setUrl(playUrl);
                    }
                    mVideoView.setInfo("(" + site + ")" + playTitle);
                    mVideoView.start();
                    mController.resetSpeed();
                    insertVod(mSourceKeyStr, mVodInfo);
                    hideTipsView();
                }
            }
        });
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
    }

    private void playNext() {
        boolean hasNext;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(this, "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex++;
        preGetPlayUrl();
    }

    private void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(this, "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        preGetPlayUrl();
    }


    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        L.d("initParse() " + useParse + "=useParse \t url =" + url);
        parseFlagStr = flag;
        mWebUrl = url;
        ParseBean parseBean = null;
        if (useParse) {
            if (autoRetryCount == 0) {
                parseBean = ApiConfig.getInstance().getDefaultParse();
            } else {
                parseBean = ApiConfig.getInstance().getmParseBeanList().get(autoRetryCount-1);
            }
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.getInstance().getmParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
        }
        if (parseBean == null) {
            parseBean = new ParseBean();
            parseBean.setType(0);
            parseBean.setUrl(playUrl);
        }
        m_bLoadFound = false;
        doParse(parseBean);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.movie_name_tv:
                if (mController.bLocked) {
                    mController.showLockImg();
                    return;
                }
                finish();
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mController.lockWarning()) {
            return;
        }
        if (System.currentTimeMillis() - firstTimes > 5000) {
            firstTimes = System.currentTimeMillis();
            UIUtil.showToast(this, "再按一次返回键退出");
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.dealKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        UiHelper.upPlayResult(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        mController.releaseController();
        if (null != mHandler) {
            mHandler.removeCallbacks(null);
        }
    }

    private void stopParse() {
        stopLoadWebView(false);
        m_bLoadFound = false;
        OkGo.getInstance().cancelTag("json_jx");
    }

    private void doParse(ParseBean parseBean) {
        stopParse();
        L.d("doParse parseBean.getType()=" + parseBean.getType() + " \t" +parseBean.getName());
        if (parseBean.getType() == 0) {
            showTipsView("缓冲中...", true, false);
            initWebViewAndLoad(parseBean.getUrl() + mWebUrl);
        } else if (parseBean.getType() == 1) { // json 解析
            showTipsView("正在解析播放地址", true, false);
            requestJx1Url(parseBean);
        } else if (parseBean.getType() == 2) { // json 扩展
            showTipsView("正在解析扩展播放地址", true, false);
            LinkedHashMap<String, String> jxsMap = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.getInstance().getmParseBeanList()) {
                if (p.getType() == 1) {
                    jxsMap.put(p.getName(), p.mixUrl());
                }
            }
            mExecutorService.execute(() -> {
                JSONObject rs = ApiConfig.getInstance().jarExt(parseBean.getUrl(), jxsMap, mWebUrl);
                if (rs == null || !rs.has("url")) {
                    errorWithRetry("解析错误", false);
                } else {
                    boolean bParseWV = (rs.optInt("parse", 0) == 1);
                    if (bParseWV) {
                        String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                        loadInWebView(wvUrl);
                    } else {
                        playVideo(rs.optString("url", ""), UiHelper.getHttpHeader(rs));
                    }
                }
            });
        } else if (parseBean.getType() == 3) { // json 聚合
            showTipsView("正在解析聚合播放地址", true, false);
            LinkedHashMap<String, HashMap<String, String>> jxsMap = new LinkedHashMap<>();
            String extendName = "";
            for (ParseBean p : ApiConfig.getInstance().getmParseBeanList()) {
                HashMap map = new HashMap<String, String>();
                map.put("url", p.getUrl());
                if (p.getUrl().equals(parseBean.getUrl())) {
                    extendName = p.getName();
                }
                map.put("type", p.getType() + "");
                map.put("ext", p.getExt());
                jxsMap.put(p.getName(), map);
            }
            String finalExtendName = extendName;
            mExecutorService.execute(() -> {
                JSONObject rs = ApiConfig.getInstance().jarExtMix(parseFlagStr + "111", parseBean.getUrl(), finalExtendName, jxsMap, mWebUrl);
                if (rs == null || !rs.has("url")) {
                    errorWithRetry("解析错误", false);
                } else {
                    if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                        runOnUiThread(() -> {
                            String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            stopParse();
                            showTipsView("缓冲中...", true, false);
                            initWebViewAndLoad(mixParseUrl);
                        });
                    } else {
                        playVideo(rs.optString("url", ""), UiHelper.getHttpHeader(rs));
                    }
                }
            });
        }
    }

    private void requestJx1Url(ParseBean parseBean) {
        L.d("OkGo get " + (parseBean.getUrl() + mWebUrl));
        OkGo.<String>get(parseBean.getUrl() + mWebUrl)
                .tag("json_jx")
                .headers(UiHelper.getHttpHeader(parseBean))
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        L.d("onSuccess json=" + json);
                        try {
                            JSONObject rs = UiHelper.jsonParse(json);
                            playVideo(rs.getString("url"), UiHelper.getHttpHeader(rs));
                        } catch (Throwable e) {
                            errorWithRetry("解析错误", false);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        L.d("onError response=" + (response == null ? "" : response.toString()));
                        errorWithRetry("解析错误", false);
                    }
                });
    }

    private void initWebViewAndLoad(String url) {
        L.d("initWebViewAndLoad()  " + url);
        if (mSysWebView == null && mXwalkWebView == null) {
            boolean useSystemWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
            if (!useSystemWebView) {
                XWalkUtils.tryUseXWalk(mContext, new XWalkUtils.XWalkState() {
                    @Override
                    public void success() {
                        doInitWebView(false);
                        loadInWebView(url);
                    }

                    @Override
                    public void fail() {
                        doInitWebView(true);
                        loadInWebView(url);
                    }

                    @Override
                    public void ignore() {
                        doInitWebView(true);
                        loadInWebView(url);
                    }
                });
            } else {
                doInitWebView(true);
                loadInWebView(url);
            }
        } else {
            loadInWebView(url);
        }
    }

    private void doInitWebView(boolean useSystemWebView) {
        if (useSystemWebView) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
        } else {
            mXwalkWebView = new MyXWalkView(mContext);
            configWebViewX5(mXwalkWebView);
        }
    }

    private void loadInWebView(String url) {
        L.d("loadInWebView() url=" + url);
        runOnUiThread(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.clearCache(true);
                mSysWebView.loadUrl(url);
            } else if (mXwalkWebView != null) {
                mXwalkWebView.stopLoading();
                mXwalkWebView.clearCache(true);
                mXwalkWebView.loadUrl(url);
            }
        });
    }

    private void stopLoadWebView(boolean bDestroy) {
        runOnUiThread(() -> {
            if (mXwalkWebView != null) {
                mXwalkWebView.stopLoading();
                mXwalkWebView.loadUrl("about:blank");
                if (bDestroy) {
                    mXwalkWebView.clearCache(true);
                    mXwalkWebView.removeAllViews();
                    mXwalkWebView.onDestroy();
                    mXwalkWebView = null;
                }
            }
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.loadUrl("about:blank");
                if (bDestroy) {
                    mSysWebView.clearCache(true);
                    mSysWebView.removeAllViews();
                    mSysWebView.destroy();
                    mSysWebView = null;
                }
            }
        });
    }

    private boolean checkVideoFormat(String url) {
//        if (mSiteBean.getType() == 3) {
//            Spider sp = ApiConfig.getInstance().getCSP(mSiteBean);
//            if (sp != null && sp.manualVideoCheck()) {
//                return sp.isVideoFormat(url);
//            }
//        }
        return DefaultConfig.isVideoFormat(url);
    }

    private void showSystemTime() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTimeTV.setText(StringUtil.getFormatTimeStr(System.currentTimeMillis()));
                mHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void showTipsView(String msg, boolean loading, boolean err) {
        mHandler.post(() -> {
            mPlayLoadTipTV.setText(msg);
            mPlayLoadTipTV.setVisibility(View.VISIBLE);
            mPlayLoadingPb.setVisibility(loading ? View.VISIBLE : View.GONE);
            mPlayErrImg.setVisibility(err ? View.VISIBLE : View.GONE);
        });
    }

    private void hideTipsView() {
        mHandler.post(() -> {
            mPlayLoadTipTV.setVisibility(View.GONE);
            mPlayLoadingPb.setVisibility(View.GONE);
            mPlayErrImg.setVisibility(View.GONE);
        });
    }

    private void configWebViewSys(WebView webView) {
        if (webView == null) return;
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        /* 添加webView配置 */
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
        // settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewX5(XWalkView xwalkView) {
        if (xwalkView == null) return;
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        xwalkView.setFocusable(false);
        xwalkView.setFocusableInTouchMode(false);
        xwalkView.clearFocus();
        xwalkView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(xwalkView, layoutParams);
        /* 添加webView配置 */
        final XWalkSettings settings = xwalkView.getSettings();
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // settings.setUserAgentString(ANDROID_UA);

        xwalkView.setBackgroundColor(Color.BLACK);
        xwalkView.setUIClient(new XWalkUIClient(xwalkView) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                return false;
            }

            @Override
            public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
                return true;
            }
        });
        mX5WebClient = new XWalkWebClient(xwalkView);
        xwalkView.setResourceClient(mX5WebClient);
    }


    //========================webView类==============================================
    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity) {
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class MyXWalkView extends XWalkView {
        public MyXWalkView(Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity) {
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class SysWebClient extends WebViewClient {
        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            L.d("shouldInterceptRequest() url");
            WebResourceResponse response = checkIsVideo(url, null);
            if (response == null) {
                return super.shouldInterceptRequest(view, url);
            } else {
                return response;
            }
        }

        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            L.d("shouldInterceptRequest() request ");
            String url = request.getUrl().toString();
            HashMap<String, String> webHeaders = new HashMap<>();
            try {
                Map<String, String> hds = request.getRequestHeaders();
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k, " " + hds.get(k));
                    }
                }
            } catch (Throwable th) {
                L.w("shouldInterceptRequest " + th.getMessage());
            }
            WebResourceResponse response = checkIsVideo(url, webHeaders);
            if (response == null) {
                return super.shouldInterceptRequest(view, request);
            } else {
                return response;
            }
        }

        private WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            L.d("checkIsVideo() url=" + url);
            if (url.endsWith("/favicon.ico")) {
                return new WebResourceResponse("image/png", null, null);
            }
            boolean isAd;
            if (!loadedUrlsMap.containsKey(url)) {
                isAd = AdBlocker.isAd(url);
                loadedUrlsMap.put(url, isAd);
            } else {
                isAd = loadedUrlsMap.get(url);
            }

            if (checkVideoFormat(url)) {
                m_bLoadFound = true;
            }
            playVideo(url, headers);
            stopLoadWebView(false);
            return isAd || m_bLoadFound ? AdBlocker.createEmptyResource() : null;
        }
    }

    class XWalkWebClient extends XWalkResourceClient {
        public XWalkWebClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {
            super.onLoadFinished(view, url);
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            L.d("shouldInterceptLoadRequest()");
            String url = request.getUrl().toString();
            if (url.endsWith("/favicon.ico")) {
                return createXWalkWebResourceResponse("image/png", null, null);
            }
            L.d("shouldInterceptLoadRequest url:" + url);
            boolean ad;
            if (!loadedUrlsMap.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrlsMap.put(url, ad);
            } else {
                ad = loadedUrlsMap.get(url);
            }
            if (!ad && !m_bLoadFound) {
                if (checkVideoFormat(url)) {
                    m_bLoadFound = true;
                    HashMap<String, String> webHeaders = new HashMap<>();
                    try {
                        Map<String, String> hds = request.getRequestHeaders();
                        for (String k : hds.keySet()) {
                            if (k.equalsIgnoreCase("user-agent")
                                    || k.equalsIgnoreCase("referer")
                                    || k.equalsIgnoreCase("origin")) {
                                webHeaders.put(k, " " + hds.get(k));
                            }
                        }
                    } catch (Throwable th) {
                        L.w(th.getMessage());
                    }
                    if (webHeaders != null && !webHeaders.isEmpty()) {
                        playVideo(url, webHeaders);
                    } else {
                        playVideo(url, null);
                    }
                    stopLoadWebView(false);
                }
            }
            return ad || m_bLoadFound ?
                    createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())) :
                    super.shouldInterceptLoadRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
            return false;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

}