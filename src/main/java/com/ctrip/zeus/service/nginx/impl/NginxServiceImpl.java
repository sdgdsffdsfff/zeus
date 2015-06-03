package com.ctrip.zeus.service.nginx.impl;

import com.ctrip.zeus.client.NginxClient;
import com.ctrip.zeus.dal.core.NginxServerDao;
import com.ctrip.zeus.dal.core.NginxServerDo;
import com.ctrip.zeus.dal.core.NginxServerEntity;
import com.ctrip.zeus.model.entity.*;
import com.ctrip.zeus.nginx.NginxOperator;
import com.ctrip.zeus.nginx.entity.NginxResponse;
import com.ctrip.zeus.nginx.entity.NginxServerStatus;
import com.ctrip.zeus.nginx.entity.TrafficStatus;
import com.ctrip.zeus.service.build.NginxConfService;
import com.ctrip.zeus.service.model.SlbRepository;
import com.ctrip.zeus.service.nginx.NginxService;
import com.ctrip.zeus.util.S;
import com.ctrip.zeus.util.TrafficStatusCollector;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author:xingchaowang
 * @date: 3/8/2015.
 */
@Service("nginxService")
public class NginxServiceImpl implements NginxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NginxServiceImpl.class);
    private static DynamicIntProperty adminServerPort = DynamicPropertyFactory.getInstance().getIntProperty("server.port", 8099);
    private static DynamicIntProperty dyupsPort = DynamicPropertyFactory.getInstance().getIntProperty("dyups.port", 8081);

    @Resource
    private SlbRepository slbRepository;
    @Resource
    private NginxConfService nginxConfService;
    @Resource
    private NginxServerDao nginxServerDao;

    @Override
    public NginxResponse writeToDisk() throws Exception {
        String ip = S.getIp();
        Slb slb = slbRepository.getBySlbServer(ip);
        Long slbId = slb.getId();
        int version = nginxConfService.getCurrentVersion(slbId);

        NginxServerDo nginxServerDo =nginxServerDao.findByIp(ip, NginxServerEntity.READSET_FULL);
        if (nginxServerDo!=null&&nginxServerDo.getVersion()>=version)
        {
            NginxResponse res = new NginxResponse();
            res.setServerIp(ip).setSucceed(true).setOutMsg("current version is lower then or equal the version used!current version ["
                    +version+"],used version ["+nginxServerDo.getVersion()+"]");
            return res;
        }

        NginxOperator nginxOperator = new NginxOperator(slb.getNginxConf(), slb.getNginxBin());

        writeConfToDisk(slbId, version, nginxOperator);

        NginxResponse response = nginxOperator.reloadConfTest();
        response.setServerIp(ip);

        //ToDo: for rollback
        return response;
    }

    @Override
    public boolean writeALLToDisk(Long slbId) throws Exception {
        return writeALLToDisk(slbId,null);
    }

    @Override
    public List<NginxResponse> writeALLToDiskListResult(Long slbId) throws Exception {
        List<NginxResponse> result = new ArrayList<>();
        writeALLToDisk(slbId,result);
        return result;
    }

    public boolean writeALLToDisk(Long slbId, List<NginxResponse> responses) throws Exception {
        List<NginxResponse> result = null;
        boolean sucess = true;
        if (responses!=null)
        {
            result = responses;
        }else {
            result = new ArrayList<>();
        }

        String ip = S.getIp();
        Slb slb = slbRepository.getById(slbId);

        List<SlbServer> slbServers = slb.getSlbServers();
        for (SlbServer slbServer : slbServers) {
            if (ip.equals(slbServer.getIp())) {
                result.add(writeToDisk());
                continue;
            }
            NginxClient nginxClient = NginxClient.getClient(buildRemoteUrl(slbServer.getIp()));
            NginxResponse response = nginxClient.write();
            result.add(response);
        }

        if (result.size()==0){
            sucess = false;
        }

        for (NginxResponse res : result)
        {
            sucess=sucess&&res.getSucceed();
        }

        return sucess;
    }

    @Override
    public NginxResponse load() throws Exception {
        String ip = S.getIp();
        Slb slb = slbRepository.getBySlbServer(ip);
        Long slbId = slb.getId();
        int version = nginxConfService.getCurrentVersion(slbId);

        NginxServerDo nginxServer =nginxServerDao.findByIp(ip, NginxServerEntity.READSET_FULL);
        if (nginxServer!=null&&nginxServer.getVersion()>=version)
        {
            NginxResponse res = new NginxResponse();
            res.setServerIp(ip).setSucceed(true).setOutMsg("current version is lower then or equal the version used,Don't update!current version ["
                    +version+"],used version ["+nginxServer.getVersion()+"]");
            return res;
        }

        NginxOperator nginxOperator = new NginxOperator(slb.getNginxConf(), slb.getNginxBin());

        // reload configuration
        NginxResponse response =  nginxOperator.reloadConf();
        response.setServerIp(ip);

        if (response.getSucceed())
        {
            // update the used version in the db
            NginxServerDo nginxServerDo =nginxServerDao.findByIp(ip, NginxServerEntity.READSET_FULL);
            nginxServerDao.updateByPK(nginxServerDo.setVersion(version), NginxServerEntity.UPDATESET_FULL);
        }
        return response;
    }



    @Override
    public List<NginxResponse> loadAll(Long slbId) throws Exception {
        List<NginxResponse> result = new ArrayList<>();
        String ip = S.getIp();
        Slb slb = slbRepository.getById(slbId);

        List<SlbServer> slbServers = slb.getSlbServers();
        for (SlbServer slbServer : slbServers) {
            if (ip.equals(slbServer.getIp())) {
                result.add(load());
                continue;
            }
            NginxClient nginxClient = NginxClient.getClient(buildRemoteUrl(slbServer.getIp()));
            NginxResponse response = nginxClient.load();
            result.add(response);
        }
        return result;

    }

    @Override
    public List<NginxResponse> writeAllAndLoadAll(Long slbId) throws Exception {
        List<NginxResponse> result = new ArrayList<>();
        if(!writeALLToDisk(slbId,result)){
            LOGGER.error("Write All To Disk Failed!");
            StringBuilder sb = new StringBuilder(128);
            sb.append("[");
            for (NginxResponse res : result)
            {
                sb.append(String.format(NginxResponse.JSON,res)).append(",\n");
            }
            sb.append("]");
            throw new Exception("Write All To Disk Failed!\nDetail:\n"+sb.toString());
        }
        result = loadAll(slbId);
        return result;
    }

    @Override
    public NginxResponse dyopsLocal(String upsName,String upsCommands) throws Exception {
        return new NginxOperator().dyupsLocal( upsName, upsCommands);
    }

    @Override
    public List<NginxResponse> dyops(Long slbId, List<DyUpstreamOpsData> dyups) throws Exception {
        List<NginxResponse> result = new ArrayList<>();
        Slb slb = slbRepository.getById(slbId);
        int version = nginxConfService.getCurrentVersion(slbId);
        boolean flag=false;
        String ip = S.getIp();

        NginxServerDo nginxServer =nginxServerDao.findByIp(ip, NginxServerEntity.READSET_FULL);
        if (nginxServer!=null&&nginxServer.getVersion()>=version)
        {
            NginxResponse res = new NginxResponse();
            res.setServerIp(ip).setSucceed(true).setOutMsg("current version is lower then or equal the version used!current version ["
                    +version+"],used version ["+nginxServer.getVersion()+"]");
            List<NginxResponse> responses = new ArrayList<>();
            responses.add(res);
            return responses;
        }

        List<SlbServer> slbServers = slb.getSlbServers();
        for (SlbServer slbServer : slbServers) {
            flag = true;
            NginxClient nginxClient = NginxClient.getClient("http://" + slbServer.getIp() + ":" + adminServerPort.get());
            for (DyUpstreamOpsData dyup : dyups){
                NginxResponse response = nginxClient.dyups(dyup.getUpstreamName(),dyup.getUpstreamCommands());
                response.setServerIp(slbServer.getIp());
                result.add(response);
                flag=flag&&response.getSucceed();
            }
            if (flag){
                // update the used version in the db
                NginxServerDo nginxServerDo =nginxServerDao.findByIp(slbServer.getIp(), NginxServerEntity.READSET_FULL);
                nginxServerDao.updateByPK(nginxServerDo.setVersion(version), NginxServerEntity.UPDATESET_FULL);
            }
        }
        return result;
    }


    @Override
    public NginxServerStatus getStatus() throws Exception {
        String ip = S.getIp();
        Slb slb = slbRepository.getBySlbServer(ip);
        NginxOperator nginxOperator = new NginxOperator(slb.getNginxConf(), slb.getNginxBin());
        return nginxOperator.getRuntimeStatus();
    }

    @Override
    public List<NginxServerStatus> getStatusAll(Long slbId) throws Exception {
        List<NginxServerStatus> result = new ArrayList<>();
        String ip = S.getIp();

        Slb slb = slbRepository.getById(slbId);
        for (SlbServer slbServer : slb.getSlbServers()) {
            if (ip.equals(slbServer.getIp())) {
                result.add(getStatus());
                continue;
            }
            NginxClient nginxClient = NginxClient.getClient(buildRemoteUrl(slbServer.getIp()));
            NginxServerStatus response = nginxClient.getNginxServerStatus();
            result.add(response);
        }
        return result;
    }

    @Override
    public List<TrafficStatus> getTrafficStatusBySlb(Long slbId) throws Exception {
        Slb slb = slbRepository.getById(slbId);
        List<TrafficStatus> list = new ArrayList<>();
        for (SlbServer slbServer : slb.getSlbServers()) {
            NginxClient nginxClient = NginxClient.getClient(buildRemoteUrl(slbServer.getIp()));
            try {
                list.addAll(nginxClient.getTrafficStatus().getStatuses());
            } catch (Exception e) {
                LOGGER.error(e.getLocalizedMessage());
            }
        }
        return list;
    }

    @Override
    public List<TrafficStatus> getLocalTrafficStatus() {
        return TrafficStatusCollector.getInstance().getResult();
    }

    private void writeConfToDisk(Long slbId, int version, NginxOperator nginxOperator) throws Exception {
        LOGGER.info("Start writing nginx configuration.");
        // write nginx conf
        writeNginxConf(slbId, version, nginxOperator);
        // write server conf
        writeServerConf(slbId, version, nginxOperator);
        // write upstream conf
        writeUpstreamConf(slbId, version, nginxOperator);
    }



    private static String buildRemoteUrl(String ip) {
        return "http://" + ip + ":" + adminServerPort.get();
    }

    private void writeNginxConf(Long slbId, int version, NginxOperator nginxOperator) throws Exception {
        String nginxConf = nginxConfService.getNginxConf(slbId, version);
        if (nginxConf == null || nginxConf.isEmpty()){
            throw new IllegalStateException("the nginx conf must not be empty!");
        }
        nginxOperator.writeNginxConf(nginxConf);
    }

    private void writeServerConf(Long slbId, int version, NginxOperator nginxOperator) throws Exception {
        List<NginxConfServerData> nginxConfServerDataList = nginxConfService.getNginxConfServer(slbId, version);
         for (NginxConfServerData d : nginxConfServerDataList) {
            nginxOperator.writeServerConf(d.getVsId(), d.getContent());
        }
    }

    private void writeUpstreamConf(Long slbId, int version, NginxOperator nginxOperator) throws Exception {
        List<NginxConfUpstreamData> nginxConfUpstreamList = nginxConfService.getNginxConfUpstream(slbId, version);
        for (NginxConfUpstreamData d : nginxConfUpstreamList) {
            nginxOperator.writeUpstreamsConf(d.getVsId(), d.getContent());
        }
    }
}
