package com.ctrip.zeus.service.model.impl;

import com.ctrip.zeus.model.entity.App;
import com.ctrip.zeus.model.entity.AppList;
import com.ctrip.zeus.service.model.AppQuery;
import com.ctrip.zeus.service.model.AppRepository;
import com.ctrip.zeus.service.model.AppSync;
import com.ctrip.zeus.service.model.ArchiveService;
import org.springframework.stereotype.Repository;
import org.unidal.dal.jdbc.DalException;

import javax.annotation.Resource;

/**
 * @author:xingchaowang
 * @date: 3/7/2015.
 */
@Repository("appRepository")
public class AppRepositoryImpl implements AppRepository {
    @Resource
    private AppSync appSync;
    @Resource
    private AppQuery appQuery;
    @Resource
    private ArchiveService archiveService;

    @Override
    public AppList list() {
        try {
            AppList appList = new AppList();

            for (App app : appQuery.getAll()) {
                appList.addApp(app);
            }
            appList.setTotal(appList.getApps().size());
            return appList;
        } catch (DalException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AppList list(String slbName, String virtualServerName) {
        try {
            AppList appList = new AppList();

            for (App app : appQuery.getBy(slbName, virtualServerName)) {
                appList.addApp(app);
            }
            appList.setTotal(appList.getApps().size());
            return appList;
        } catch (DalException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public App get(String appName) {
        try {
            return appQuery.get(appName);
        } catch (DalException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addOrUpdate(App app) {
        try {
            appSync.sync(app);
            archiveService.archiveApp(app);
        } catch (DalException e) {
            e.printStackTrace();
        }
    }
}