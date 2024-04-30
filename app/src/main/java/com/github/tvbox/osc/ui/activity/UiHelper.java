package com.github.tvbox.osc.ui.activity;

import android.content.Context;

import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.PlayBean;
import com.github.tvbox.osc.bean.SiteBean;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lzy.okgo.model.HttpHeaders;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.uisupport.UiConstans;
import com.uisupport.update.HotWords;
import com.uisupport.update.HttpUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import xyz.doikki.videoplayer.util.PrintUtil;

public class UiHelper {

    public static void upPlayResult(Context ctx) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                HotWords result = HttpUtil.postPlayResult(ctx, UiConstans.UP_PLAY_RESULT_URL
                        , PrintUtil.successMap, PrintUtil.playErrorMap);
            }
        }.start();
    }


    public static String getBeanStr(SiteBean bean){
        if(bean == null) return "";
        return "name="+ bean.getName() +
                "\t key="+ bean.getKey() +
                "\t type="+ bean.getType() +
                "\t api="+ bean.getApi() ;
    }

    public static JSONObject jsonParse(String json) throws JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        String url = jsonPlayData.getString("url");
        String msg = jsonPlayData.optString("msg", "");
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        JSONObject headers = new JSONObject();
        String ua = jsonPlayData.optString("user-agent", "");
        if (ua.trim().length() > 0) {
            headers.put("User-Agent", " " + ua);
        }
        String referer = jsonPlayData.optString("referer", "");
        if (referer.trim().length() > 0) {
            headers.put("Referer", " " + referer);
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        return taskResult;
    }

    public static boolean xmlHasClasses(String xml) {
        try {
            XStream xstream = new XStream(new DomDriver());//创建Xstram对象
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsSortXml.class);
            xstream.ignoreUnknownElements();
            AbsSortXml absXml = (AbsSortXml) xstream.fromXML(xml);
            if (null != absXml && null != absXml.classes && !NullUtil.isNull(absXml.classes.sortList))    return true;
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean jsonHasClasses(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            AbsSortJson sortJson = new Gson().fromJson(obj, new TypeToken<AbsSortJson>() {
            }.getType());
            if (sortJson != null && !NullUtil.isNull(sortJson.classes)) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public static boolean isJson(String json) {
        boolean isJ = true;
        try {
            JSONObject jsonPlayData = new JSONObject(json);
        } catch (Throwable e) {
            isJ = false;
        }
        return isJ;
    }

    public static boolean canPlay(Movie.Video video) {
        boolean bCan = true;
        if (!NullUtil.isNull(video.type)) {
        }
        return bCan;
    }

    public static boolean canPlayName(Movie.Video video) {
        boolean bCan = true;
        if (!NullUtil.isNull(video.name)) {
        }
        return bCan;
    }

    public static List<Movie.Video> filterVideoList(AbsXml absXml, String searchWord) {
        List<Movie.Video> videos = new ArrayList<>();
        for (Movie.Video video : absXml.movie.videoList) {
            L.d("video.type=" + video.type + "  note=" + video.note);
            if (video.name.contains(searchWord) || video.actor.contains(searchWord)
                    || video.director.contains(searchWord) || video.des.contains(searchWord)
            ) {
                if (UiHelper.canPlay(video) && UiHelper.canPlayName(video)) {
                    videos.add(video);
                }
            }
        }
        return videos;
    }

    public static HashMap<String, String> getHttpHeader(JSONObject rs){
        HashMap<String, String> headers = null;
        if (rs.has("header")) {
            try {
                JSONObject hds = rs.getJSONObject("header");
                Iterator<String> keys = hds.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (headers == null) {
                        headers = new HashMap<>();
                    }
                    headers.put(key, hds.optString(key));
                }
            } catch (Throwable th) {
                L.e(th.getMessage());
            }
        }
        return  headers;
    }
    public static HttpHeaders getHttpHeader(ParseBean parseBean){
        HttpHeaders reqHeaders = new HttpHeaders();
        try {
            // 解析ext
            JSONObject jsonObject = new JSONObject(parseBean.getExt());
            if (jsonObject.has("header")) {
                JSONObject headerJson = jsonObject.optJSONObject("header");
                Iterator<String> keys = headerJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    reqHeaders.put(key, headerJson.optString(key, ""));
                }
            }
        } catch (Throwable e) {
            L.e("getHttpHeader "+e.getMessage());
        }
        return  reqHeaders;
    }

    public static List<Movie.Video.UrlsBean.FlagUrlsInfo.NameUrlBean> getUrlsList(String urls){
        String[] strList;
        if (urls.contains("#")) {
            strList = urls.split("#");
        } else {
            strList = new String[]{urls};
        }
        List<Movie.Video.UrlsBean.FlagUrlsInfo.NameUrlBean> nameUrls = new ArrayList<>();
        for (String s : strList) {
            if (s.contains("$")) {
                String[] ss = s.split("\\$");
                if (ss.length >= 2) {
                    nameUrls.add(new Movie.Video.UrlsBean.FlagUrlsInfo.NameUrlBean(ss[0], ss[1]));
                }
            } else {
                nameUrls.add(new Movie.Video.UrlsBean.FlagUrlsInfo.NameUrlBean("清晰", s));
            }
        }
        return nameUrls;
    }

    public static PlayBean getPlayBean(JSONObject info)     {
        PlayBean playBean =new PlayBean();

        playBean.progressKeyStr = info.optString("proKey", null);
        playBean.bNeedParse = info.optString("parse", "1").equals("1");
        playBean.bNeedJx = info.optString("jx", "0").equals("1");
        playBean.playSubtitleStr = info.optString("subt", "");
        playBean.playUrl = info.optString("playUrl", "");
        playBean.flag = info.optString("flag");
        playBean.url = info.optString("url");
        if (info.has("header")) {
            try {
                String headerStr= info.optString("header");
                L.d("headerStr="+headerStr);
                if(!NullUtil.isNull(headerStr)) {
                    JSONObject hds = new JSONObject(headerStr);
                    if (null != hds) {
                        playBean.headersMap = new HashMap<>();
                        Iterator<String> keys = hds.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            playBean.headersMap.put(key, hds.optString(key));
                        }
                    }
                }
            } catch (Exception e) {
                L.e(e);
            }
        }
        return playBean;
    }

}
