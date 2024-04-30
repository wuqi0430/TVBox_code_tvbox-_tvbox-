package com.github.tvbox.osc.data;

import com.github.tvbox.osc.cache.Cache;
import com.github.tvbox.osc.cache.CacheDao;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.cache.VodCollectDao;
import com.github.tvbox.osc.cache.VodRecord;
import com.github.tvbox.osc.cache.VodRecordDao;

import androidx.room.Database;
import androidx.room.RoomDatabase;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Database(entities = {Cache.class, VodRecord.class, VodCollect.class}, version = 1)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();
}
