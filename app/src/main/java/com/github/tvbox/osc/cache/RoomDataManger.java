package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class RoomDataManger {
    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record == null) {
            record = new VodRecord();
        }
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.dataJson = getVodInfoGson().toJson(vodInfo);
        AppDataManager.get().getVodRecordDao().insert(record);
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
        try {
            if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (vodInfo.name == null)
                    return null;
                return vodInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodRecordDao().delete(record);
        }
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        List<VodRecord> recordList = AppDataManager.get().getVodRecordDao().getAll(limit);
        List<VodInfo> vodInfoList = new ArrayList<>();
        if (recordList != null) {
            for (VodRecord record : recordList) {
                VodInfo info = null;
                try {
                    if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        info.sourceKey = record.sourceKey;
                        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(info.sourceKey);
                        if (sourceBean == null || info.name == null)
                            info = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (info != null)
                    vodInfoList.add(info);
            }
        }
        return vodInfoList;
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            return;
        }
        record = new VodCollect();
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.name = vodInfo.name;
        record.pic = vodInfo.pic;
        AppDataManager.get().getVodCollectDao().insert(record);
    }

    public static void deleteVodCollect(int id) {
        AppDataManager.get().getVodCollectDao().delete(id);
    }

    public static List<VodCollect> getAllVodCollect() {
        return AppDataManager.get().getVodCollectDao().getAll();
    }
}