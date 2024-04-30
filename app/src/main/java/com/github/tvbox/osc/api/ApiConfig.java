package com.github.tvbox.osc.api;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.http.HttpDoRequest;
import com.lcstudio.commonsurport.support.comm.Downloader;
import com.lcstudio.commonsurport.util.AssetsLoader;
import com.lcstudio.commonsurport.util.MyFilesManager;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.uisupport.UiConstans;
import com.uisupport.update.Initer;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiConfig {

    /*
      配置文件链接地址，可以自行更改
     */
    public static final String DEFAULT_URL = "请输入配置文件地址";
//    public static final String DEFAULT_URL = "http://www.tvboxx.top/tv/xiong.json";
//    public static final String DEFAULT_URL = "https://gitee.com/fujianjun0429/test/blob/master/site.json";

    public static final String PRE_KEY_CONFIG = "pre_key_config";
    private static ApiConfig instance = null;
    private final LinkedHashMap<String, SiteBean> mSiteBeanMap;
    private ParseBean mDefaultParseBean;
    private final List<LiveChannelGroup> mLiveChannelGroupList;
    private final List<ParseBean> mParseBeanList;
    private List<String> mVipFlagList;
    private List<IJKCode> mIjkCodes;
    private String mSpider = null;
    private final JarLoader mJarLoader;
    private boolean bInit = false;

    public static ApiConfig getInstance() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    private ApiConfig() {
        mSiteBeanMap = new LinkedHashMap<>();
        mLiveChannelGroupList = new ArrayList<>();
        mParseBeanList = new ArrayList<>();
        mJarLoader = new JarLoader();
    }

    public void loadSitesFile(Activity activity, LoadConfigCallback callback) {
        L.d("loadSitesFile start");
        String apiUrl = Hawk.get(HawkConfig.API_URL, UiConstans.DEFAULT_URL);
        SPDataUtil spDataUtil = new SPDataUtil(activity.getBaseContext());
        String configContent = spDataUtil.getStringValue(PRE_KEY_CONFIG);
        Initer.init(activity);
        new Thread() {
            @Override
            public void run() {
                super.run();
                String result = "";
                if (!NullUtil.isNull(configContent) && UiHelper.isJson(configContent)) {
                    result = configContent;
                } else {
                    String r = HttpDoRequest.getInstance(activity.getApplicationContext()).doGetRequestInOkGo(apiUrl,
                            null, false, 5 * 60 * 1000);
                    if (!NullUtil.isNull(r) && UiHelper.isJson(r)) {
                        //取到服务器内容才用
                        result = r;
                    } else {
                        AssetsLoader assetsLoader = new AssetsLoader(activity.getApplicationContext());
                        result = assetsLoader.getStrFromAssetsFile("site.json");
                    }
                }
                parseSiteFile(apiUrl, result);
                callback.success();
            }
        }.start();
    }

    public void loadJar(Context cxt, boolean useCache, String spider, LoadConfigCallback callback) {
        L.d("loadJar");
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";

        Downloader downloader = new Downloader(cxt);
        downloader.startDownload2(jarUrl, ".jar", new Downloader.DownLister() {
            @Override
            public void onSuccess(String name, String fileName) {
                if (mJarLoader.load(fileName)) {
                    L.d("loadJar onSuccess() fileName=" + fileName);
                } else {
                    L.d("loadJar error() fileName=" + fileName);
                }
                callback.success();
            }

            @Override
            public void onFailed(String name, String fileName) {
                L.d("loadJar download onFailed() fileName=" + fileName);
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        MyFilesManager.copyFileFromAssets(App.getInstance(), "tvbox.jar",
                                App.getInstance().getFilesDir().getAbsolutePath(), "csp.jar");
                        File cacheFile = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");
                        if (mJarLoader.load(cacheFile.getAbsolutePath())) {
                            L.d("loadJar onSuccess() fileName=" + fileName);
                            callback.success();
                        }
                    }
                }.start();
            }

            @Override
            public void onProgress(String name, String fileName, float percent) {
            }
        });
    }


    private void parseSiteFile(String apiUrl, String jsonStr) {
        if (NullUtil.isNull(jsonStr)) return;
        try {
            mSiteBeanMap.clear();
            JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
            mSpider = DefaultConfig.safeJsonString(jsonObject, "spider", "");
            L.d("mSpider=" + mSpider);
            // 远端站点源
            for (JsonElement opt : jsonObject.get("sites").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                SiteBean bean = new SiteBean();
                String siteKey = obj.get("key").getAsString().trim();
                bean.setKey(siteKey);
                if(NullUtil.isNull(siteKey)) continue;//空key不要
                bean.setName(obj.get("name").getAsString().trim());
                bean.setType(obj.get("type").getAsInt());
                bean.setApi(obj.get("api").getAsString().trim());
                bean.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
                bean.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
                bean.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
                bean.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
                bean.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
                bean.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
                mSiteBeanMap.put(siteKey, bean);
            }

            // 需要使用vip解析的flag
            mVipFlagList = DefaultConfig.safeJsonStringList(jsonObject, "flags");
            // 解析地址
            for (JsonElement opt : jsonObject.get("parses").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                mParseBeanList.add(pb);
                L.d(pb.getName() + "\t" + pb.getUrl());
            }
            // 获取默认解析
            if (!NullUtil.isNull(mParseBeanList)) {
                String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
                if (!TextUtils.isEmpty(defaultParse)) {
                    for (ParseBean pb : mParseBeanList) {
                        if (pb.getName().equals(defaultParse)) {
                            setDefaultParse(pb);
                        }
                    }
                }
                if (mDefaultParseBean == null) {
                    setDefaultParse(mParseBeanList.get(0));
                }
            }
            // 直播源
            mLiveChannelGroupList.clear();           //修复从后台切换重复加载频道列表
            try {
                String lives = jsonObject.get("lives").getAsJsonArray().toString();
                int index = lives.indexOf("proxy://");
                if (index != -1) {
                    int endIndex = lives.lastIndexOf("\"");
                    String url = lives.substring(index, endIndex);
                    url = DefaultConfig.checkReplaceProxy(url);

                    //clan
                    String extUrl = Uri.parse(url).getQueryParameter("ext");
                    if (extUrl != null && !extUrl.isEmpty()) {
                        String extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                        if (extUrlFix.startsWith("clan://")) {
                            extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix);
                            extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                            url = url.replace(extUrl, extUrlFix);
                        }
                    }
                    LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
                    liveChannelGroup.setGroupName(url);
                    mLiveChannelGroupList.add(liveChannelGroup);
                    L.d("live channel:" + liveChannelGroup.getGroupName());
                } else {
                    loadLives(jsonObject.get("lives").getAsJsonArray());
                }
            } catch (Throwable th) {
               L.e(th);
            }
            // 广告地址
            JsonArray ads =  jsonObject.getAsJsonArray("ads");
            if(null != ads) {
                for (JsonElement host : ads) {
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
            // IJK解码配置
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
            mIjkCodes = new ArrayList<>();
            for (JsonElement opt : jsonObject.get("ijk").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                mIjkCodes.add(codec);
                L.d("IJK name=" + codec.getName());
            }
            if (!foundOldSelect && mIjkCodes.size() > 0) {
                mIjkCodes.get(0).selected(true);
            }
        } catch (Exception e) {
            L.e(e);
        }
    }

    public void loadLives(JsonArray livesArray) {
        L.d("loadLives()");
        mLiveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1) {
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            } else {
                liveChannelGroup.setGroupPassword("");
            }
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                L.d("urls="+urls);
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    L.d("url="+url);
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1) {
                        sourceNames.add(splitText[1]);
                    } else {
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    }

                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            mLiveChannelGroupList.add(liveChannelGroup);
            L.d("live group name:"+liveChannelGroup.getGroupName());
        }
    }

    public String getmSpider() {
        if (null == mSpider) return "";
        return mSpider;
    }

    public Spider getCSP(SiteBean sourceBean) {
        return mJarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt());
    }

    public Object[] proxyLocal(Map param) {
        return mJarLoader.proxyInvoke(param);
    }

    public JSONObject jarExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return mJarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jarExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return mJarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();
    }

    public interface InitHomeCallback {
        void success();
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParseBean != null) {
            this.mDefaultParseBean.setDefault(false);
        }
        this.mDefaultParseBean = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParseBean;
    }

    public List<SiteBean> getSiteBeanList() {
        return new ArrayList<>(mSiteBeanMap.values());
    }

    public List<ParseBean> getmParseBeanList() {
        return mParseBeanList;
    }

    public List<String> getmVipFlagList() {
        return mVipFlagList;
    }

    public SiteBean getHomeBean() {
        String userKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        SiteBean userHomeBean = getSiteBean(userKey);//Map有空key，会有冲突
        if (userHomeBean != null) {
            //用户设置的最优先
            L.d("  return userHomeBean; ");
            return userHomeBean;
        }

        String homeKey = Hawk.get(HawkConfig.HOME_API, "");
        SiteBean homeBean = getSiteBean(homeKey);
        if (homeBean != null) {
            //自动设置的其次
            L.d("   return homeBean;");
            return homeBean;
        }
        List<SiteBean> beans = new ArrayList<>(mSiteBeanMap.values());
        if (!NullUtil.isNull(beans)) {
            //都没有设置
            L.d("  beans.get(0);");
            return beans.get(0);
        } else {
            return new SiteBean();
        }
    }

    public void setHomeBean(SiteBean bean) {
        L.d("setHomeBean= "+bean.getKey() + "\t"+bean.getName());
        Hawk.put(HawkConfig.HOME_API, bean.getKey());
    }

    public void setUserHomeBean(SiteBean bean) {
        Hawk.put(HawkConfig.USER_HOME_API, bean.getKey());
    }

    public void initHomeBean(InitHomeCallback callback) {
        String userHomeKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        SiteBean userHomeBean = getSiteBean(userHomeKey);
        if (!NullUtil.isNull(userHomeKey) && null != userHomeBean) {
            //用户设置过home，直接返回
            L.d("initHomeBean userHomeBean callback.success() ");
            callback.success();
            return;
        }
        //自动设置home
        bInit = false;
        List<SiteBean> beans = new ArrayList<>(mSiteBeanMap.values());
        if (!NullUtil.isNull(beans)) {
            ExecutorService mExecutorService = Executors.newFixedThreadPool(3);
            for (SiteBean bean : beans) {
                mExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (bInit) return;//初始过，不再继续
                        int type = bean.getType();
                        if (type == 3) {
                            Spider sp = ApiConfig.getInstance().getCSP(bean);
                            String sortJson = sp.homeContent(true);
                            L.d("initHomeBean " + UiHelper.getBeanStr(bean) + "  sortJson=" + sortJson);
                            if (!NullUtil.isNull(sortJson) && UiHelper.isJson(sortJson) && UiHelper.jsonHasClasses(sortJson)) {
                                if (bInit) return;//初始过，不再初始
                                setHomeBean(bean);
                                callback.success();
                                mExecutorService.shutdown();
                                bInit = true;
                            }
                        } else {
                            OkGo.<String>get(bean.getApi())
                                    .tag("_initHome")
                                    .execute(new AbsCallback<String>() {
                                        @Override
                                        public String convertResponse(okhttp3.Response response) throws Throwable {
                                            if (response.body() != null) {
                                                return response.body().string();
                                            } else {
                                                L.d(" initHomeBean() 网络请求失败");
                                                throw new IllegalStateException("网络请求失败");
                                            }
                                        }

                                        @Override
                                        public void onSuccess(Response<String> response) {
                                            String res = response.body();
                                            L.d("initHomeBean " + UiHelper.getBeanStr(bean) + "  xml=" + res);
                                            if (!NullUtil.isNull(res) && (UiHelper.xmlHasClasses(res) || UiHelper.jsonHasClasses(res))) {
                                                if (bInit) return;//初始过，不再初始
                                                OkGo.getInstance().cancelTag("_initHome");
                                                setHomeBean(bean);
                                                callback.success();
                                                bInit = true;
                                            }
                                        }

                                        @Override
                                        public void onError(Response<String> response) {
                                            super.onError(response);
                                        }
                                    });
                        }
                    }
                });
            }
        }
    }

    public SiteBean getSiteBean(String key) {
        if (!mSiteBeanMap.containsKey(key)) {
            return null;
        }
        return mSiteBeanMap.get(key);
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return mLiveChannelGroupList;
    }

    public List<IJKCode> getmIjkCodes() {
        return mIjkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        if(NullUtil.isNull(mIjkCodes)){
            return new IJKCode();
        }
        for (IJKCode code : mIjkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return mIjkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }
}