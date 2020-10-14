package com.ctrip.zeus.service.nginx.impl;

import com.ctrip.zeus.client.AbstractRestClient;
import com.ctrip.zeus.dao.entity.*;
import com.ctrip.zeus.dao.mapper.CertCertificateSlbServerRMapper;
import com.ctrip.zeus.dao.mapper.CertCertificateVsRMapper;
import com.ctrip.zeus.exceptions.ValidationException;
import com.ctrip.zeus.model.Certificate;
import com.ctrip.zeus.model.Property;
import com.ctrip.zeus.model.model.Domain;
import com.ctrip.zeus.model.model.Slb;
import com.ctrip.zeus.model.model.SlbServer;
import com.ctrip.zeus.model.model.VirtualServer;
import com.ctrip.zeus.service.CertificateResourceService;
import com.ctrip.zeus.service.model.IdVersion;
import com.ctrip.zeus.service.model.SelectionMode;
import com.ctrip.zeus.service.model.SlbRepository;
import com.ctrip.zeus.service.model.VirtualServerRepository;
import com.ctrip.zeus.service.nginx.CertificateHelper;
import com.ctrip.zeus.service.nginx.CertificateService;
import com.ctrip.zeus.service.nginx.opensource.CertInstallClient;
import com.ctrip.zeus.service.query.SlbCriteriaQuery;
import com.ctrip.zeus.service.query.VirtualServerCriteriaQuery;
import com.ctrip.zeus.support.C;
import com.ctrip.zeus.support.ObjectJsonParser;
import com.ctrip.zeus.tag.PropertyService;
import com.ctrip.zeus.tag.TagService;
import com.ctrip.zeus.util.CertConstants;
import com.ctrip.zeus.util.CertUtil;
import com.ctrip.zeus.util.IOUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

import static com.ctrip.zeus.auth.util.AuthTokenUtil.getDefaultHeaders;

@Service("certificateService")
public class CertificateServiceImpl implements CertificateService {

    @Resource
    protected CertificateResourceService certificateResourceService;
    @Resource
    protected TagService tagService;
    @Resource
    protected VirtualServerCriteriaQuery virtualServerCriteriaQuery;
    @Resource
    protected SlbCriteriaQuery slbCriteriaQuery;
    @Resource
    protected SlbRepository slbRepository;
    @Resource
    protected CertCertificateVsRMapper certVsRMapper;
    @Resource
    protected CertCertificateSlbServerRMapper certificateSlbServerRMapper;
    @Resource
    protected CertificateHelper certificateHelper;
    @Resource
    protected VirtualServerRepository virtualServerRepository;
    @Resource
    private CertInstallClient certInstallClient;
    private final DynamicLongProperty INSTALL_CERT_TIMEOUT = DynamicPropertyFactory.getInstance().getLongProperty("install.cert.timeout", 3000);
    @Resource
    private PropertyService propertyService;
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected static final String STATUS_CANARY = "CANARY";
    protected static final String STATUS_ACTIVATED = "ACTIVATED";
    protected static final String DATA_CERT = "CERT";


    @VisibleForTesting
    public void setCertInstallClient(CertInstallClient certInstallClient) {
        this.certInstallClient = certInstallClient;
    }

    @Override
    public Long updateCertState(Long certId, boolean state) throws Exception {
        return null;
    }

    @Override
    public Long add(String cert, String key, List<String> domains, String cid) throws Exception {
        if (cert == null || key == null || domains == null || domains.size() == 0) {
            return null;
        }
        return certificateResourceService.add(cert, key, domains, cid);
    }

    // Fetch all records from db, and transform it to standard Certificate instance
    // Implementation could leave relevant vs and slb server alone.
    protected List<Certificate> fetchAllEntities(Boolean withBlobs, Date expire) {
        return certificateResourceService.all(withBlobs, expire, null);
    }

    @Override
    public List<Certificate> all(Boolean withBlobs, Date certExpire, List<String> domains) throws Exception {
        // service implementation which should be open source, thus fetch data from cert_new_certificate
        List<Certificate> certificates;
        if (CollectionUtils.isEmpty(domains)) {
            certificates = fetchAllEntities(withBlobs, certExpire);
        } else {
            certificates = getByExactDomains(domains.toArray(new String[0]), withBlobs);
        }

        if (certExpire != null) {
            certificates.removeIf(certificate -> certificate.getExpireTime() != null && certificate.getExpireTime().compareTo(certExpire) <= 0);
        }
        if (certExpire != null) {
            certificates.removeIf(certificate -> certificate.getExpireTime() != null && certificate.getExpireTime().compareTo(certExpire) <= 0);
        }

        return certificates;
    }

    @Override
    public Certificate getByCertId(Long certId, Boolean withBlobs) {
        return certificateResourceService.get(certId, withBlobs);
    }

    @Override
    public Set<Long> getVsIdsByCertId(Long certId) throws Exception {
        return new HashSet<>(certificateHelper.getVsIdsByCertId(certId));
    }

    @Override
    public Map<Long, List<Long>> batchSearchVsesByCert(List<Long> certIds) {
        Map<Long, Set<Long>> temp = certificateHelper.queryVsIdsForCerts(certIds);
        Map<Long, List<Long>> result = new HashMap<>(temp.size());
        temp.forEach((certId, vsIds) -> result.put(certId, new ArrayList<>(vsIds)));
        return result;
    }

    @Override
    public Long getMaxCertIdByDomain(String domain) throws Exception {
        if (Strings.isNullOrEmpty(domain)) {
            return null;
        }
        domain = CertUtil.standardizeDomain(domain);

        String tag = CertUtil.buildTagOf(domain);

        List<Long> targets = tagService.query(tag, CertConstants.ITEM_TYPE);
        if (targets == null || targets.size() == 0) {
            return null;
        }

        Long[] certIds = targets.toArray(new Long[0]);
        Arrays.sort(certIds);

        return certIds[certIds.length - 1];
    }

    @Override
    public CertCertificateWithBLOBs getDefaultCert(Long slbId) throws Exception {
        Property p = propertyService.getProperty("zone", slbId, "slb");

        String defaultDomain = p == null ? "_localhost" : DynamicPropertyFactory.getInstance().getStringProperty("slb.certificate.default.zone." + p.getValue(), "_localhost").get();
        Long certId = getMaxCertIdByDomain(defaultDomain);
        return C.toCertCertificateWithBlobs(getByCertId(certId, true));
    }

    @Override
    public List<Certificate> getByExactDomains(String[] domains, Boolean withBlobs) throws Exception {
        // Every string in domains is a compound '|'-joined domain, e.g. 'a.com|b.com'
        // For default, state is all true for all certificate
        if (domains == null || domains.length == 0) {
            return new ArrayList<>();
        }
        withBlobs = withBlobs == null ? false : withBlobs;

        List<String> tags = Arrays.stream(domains).map(CertUtil::buildTagOf).collect(Collectors.toList());
        try {
            Set<Long> certIds = tagService.unionQuery(tags, CertConstants.ITEM_TYPE);
            List<Certificate> certificates = getCertsByIds(new ArrayList<>(certIds), false);
            if (!withBlobs) {
                for (Certificate certificate : certificates) {
                    certificate.setCertData(null);
                    certificate.setKeyData(null);
                }
            }

            return certificates;
        } catch (Exception e) {
            logger.warn("exception happens when call tagService.unionQuery. message: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> installCanaryCertificate(Long vsId, Long certId, double rate) throws Exception {
        if (certId == null || vsId == null) {
            throw new ValidationException("VsId And certId Can Not Be Null.");
        }

        Set<String> serverIps = getSlbServers(vsId);

        long canaryCount = (long) (serverIps.size() * rate);
        if (canaryCount == 0) {
            canaryCount = 1;
        }
        List<String> canaryIps = new ArrayList<>();
        for (String ip : serverIps) {
            if (canaryCount == 0) break;
            canaryIps.add(ip);
            canaryCount--;
        }
        Certificate cert = getByCertId(certId, true);
        if (cert == null) {
            throw new ValidationException("[CertificateServiceImpl] Cert can not be found using certId: " + certId);
        }

        CertCertificateVsR newVersionVs = CertCertificateVsR.builder().vsId(vsId).certId(cert.getId()).status(STATUS_CANARY).build();
        CertCertificateVsR maxVersionVs = maxVersionForCertVs(vsId);
        if (maxVersionVs != null && STATUS_CANARY.equalsIgnoreCase(maxVersionVs.getStatus())) {
            throw new ValidationException("There is an Canary version.For VS:" + vsId);
        }
        if (maxVersionVs == null) {
            newVersionVs.setVersion(1L);
        } else {
            newVersionVs.setVersion(maxVersionVs.getVersion() + 1);
        }

        newVersionVs.setId(null);
        certVsRMapper.insert(newVersionVs);

        remoteInstall(vsId, canaryIps, cert, true);
        return canaryIps;
    }

    @Override
    public List<String> getCanaryServerIps(Long vsId, Long certId) throws Exception {
        // If vsId is given, first find related canary certificate's id by vsId, then find server ips by certificate id
        // If vsId is not given, use certId given to search for server ips.
        if (vsId == null) {
            return certificateHelper.getSlbServersByCertId(certId);
        } else {
            Map<Long, Map<String, Long>> certIdMap = getCertIdsByVsIds(new Long[]{vsId});
            if (certIdMap == null || !certIdMap.containsKey(vsId)) {
                throw new ValidationException("[CertificateServiceImpl] Can not find cert data of vs. VsId: " + vsId);
            }
            Map<String, Long> temp = certIdMap.get(vsId);
            if (!temp.containsKey(STATUS_CANARY)) {
                throw new ValidationException("[CertificateServiceImpl] No canary-version certificate exists for vs. VsId: " + vsId);
            }
            return certificateHelper.getSlbServersByCertId(temp.get(STATUS_CANARY));
        }
    }

    @Override
    public Long activateCertificate(Long vsId, Long certId, boolean force) throws Exception {
        Long result = doActivateCertificate(vsId, certId, force);

        certificateHelper.taggingDomain(certId, vsId);
        return result;
    }

    protected Long doActivateCertificate(Long vsId, Long certId, boolean force) throws Exception {
        if (vsId == null) {
            throw new ValidationException("VsId Can Not Be Null.");
        }

        List<String> slbServerIps = new ArrayList<>(getSlbServers(vsId));

        CertCertificateVsR maxVersionVs = maxVersionForCertVs(vsId);
        if (force) {
            if (certId == null) {
                throw new ValidationException("CertId can not Be Null In Case Of Force Activate.");
            }

            Certificate certificate;
            certificate = getByCertId(certId, true);
            if (certificate == null) {
                throw new NotFoundException("Not Found Cert Data.Please check your certId.CertId:" + maxVersionVs.getCertId() + ";CertId:" + certId);
            }
            CertCertificateVsR newVersionVs = CertCertificateVsR.builder().status(STATUS_CANARY).vsId(vsId).certId(certificate.getId()).build();
            if (maxVersionVs == null) {
                newVersionVs.setVersion(1L);
            } else {
                newVersionVs.setVersion(maxVersionVs.getVersion() + 1);
            }

            certVsRMapper.insert(newVersionVs);
            maxVersionVs = newVersionVs;
        } else if (maxVersionVs == null || !STATUS_CANARY.equalsIgnoreCase(maxVersionVs.getStatus())) {
            throw new ValidationException("Not Found Canary Version For Vs.VsId:" + vsId + ";CertId:" + certId);
        }

        if (certId != null && !certId.equals(maxVersionVs.getCertId())) {
            throw new ValidationException("CertId Is Not An Canary Version.CertId:" + certId + "; Canary Version:" + maxVersionVs.getCertId());
        }
        Certificate certificate = getByCertId(certId, true);
        if (certificate == null) {
            throw new NotFoundException("Not Found Cert Data.Please check your certId.CertId:" + maxVersionVs.getCertId() + ";CertId:" + certId);
        }
        maxVersionVs.setStatus(STATUS_ACTIVATED);
        remoteInstall(vsId, slbServerIps, certificate, true);

        certVsRMapper.updateByPrimaryKey(maxVersionVs);

        return certificate.getId();
    }

    @Override
    public Map<Long, Map<String, Certificate>> findCert(Long[] vsIds, boolean withCert) throws Exception {
        Map<Long, Map<String, Long>> vsIdCertIdMap = getCertIdsByVsIds(vsIds);

        Set<Long> certIds = new HashSet<>();
        vsIdCertIdMap.forEach((vsId, statusCertIdMap) -> certIds.addAll(statusCertIdMap.values()));

        List<Certificate> certificates = getCertsByIds(new ArrayList<>(certIds), false);
        Map<Long, Certificate> certById = new HashMap<>(certificates.size());
        certificates.forEach(certificate -> certById.put(certificate.getId(), certificate));

        Map<Long, Map<String, Certificate>> results = new HashMap<>(vsIdCertIdMap.size());
        vsIdCertIdMap.forEach((vsId, statusCertIdMap) -> {
            Map<String, Certificate> statusCertMap = new HashMap<>(statusCertIdMap.size());
            statusCertIdMap.forEach((status, certId) -> statusCertMap.put(status, certById.get(certId)));
            results.put(vsId, statusCertMap);
        });

        return results;
    }

    /*
     * @Description find vs's activated-versioned certificates
     * @return
     **/
    @Override
    public Map<Long, Certificate> getActivatedCerts(Long[] vsIds) throws Exception {
        if (vsIds == null || vsIds.length <= 0) {
            return new HashMap<>();
        }
        Map<Long, Map<String, Long>> vsIdCertIdMap = getCertIdsByVsIds(vsIds);
        Map<Long, Long> activatedCertVsMap = new HashMap<>(vsIds.length);

        for (Long vsId : vsIdCertIdMap.keySet()) {
            Map<String, Long> temp = vsIdCertIdMap.get(vsId);
            Long certId = temp.getOrDefault(STATUS_ACTIVATED, null);
            if (certId != null) {
                activatedCertVsMap.put(certId, vsId);
            }
        }

        List<Certificate> certificates = getCertsByIds(new ArrayList<>(activatedCertVsMap.keySet()), false);
        Map<Long, Certificate> vsCertMap = new HashMap<>(vsIds.length);
        for (Certificate certificate : certificates) {
            vsCertMap.put(activatedCertVsMap.get(certificate.getId()), certificate);
        }
        return vsCertMap;
    }

    protected List<Certificate> getCertsByIds(List<Long> ids, boolean fillInVsesAndServers) {
        if (ids == null || ids.size() == 0) {
            return new ArrayList<>();
        }

        // Open source version. Read from cert_new_certificate
        List<Certificate> certificates = certificateResourceService.batchGet(ids);
        if (fillInVsesAndServers) {
            certificateHelper.fillVsesAndServers(certificates);
        }

        return certificates;
    }

    @Override
    public Long getActivatedCertIdByVsId(Long vsId) throws Exception {
        if (vsId == null) {
            return null;
        }
        Map<Long, Map<String, Long>> vsIdCertIdsMap = getCertIdsByVsIds(new Long[]{vsId});

        Map<String, Long> temp = vsIdCertIdsMap.get(vsId);
        return temp == null ? null : temp.getOrDefault(STATUS_ACTIVATED, null);
    }

    @Override
    public Map<Long, Map<String, Long>> getCertIdsByVsIds(Long[] vsIds) throws Exception {
        Map<Long, Map<String, Long>> results = new HashMap<>();

        vsIds = vsIds == null ? new Long[0] : vsIds;
        if (vsIds.length <= 0) {
            return results;
        }

        List<CertCertificateVsR> records = certVsRMapper.selectByExample(CertCertificateVsRExample.newAndCreateCriteria().andVsIdIn(Arrays.asList(vsIds)).example());
        Map<Long, List<CertCertificateVsR>> recordsByVs = new HashMap<>();
        for (CertCertificateVsR record : records) {
            recordsByVs.putIfAbsent(record.getVsId(), new ArrayList<>());
            recordsByVs.get(record.getVsId()).add(record);
        }

        recordsByVs.forEach((vsId, specificRecords) -> {
            Map<String, Long> temp = new HashMap<>();

            CertCertificateVsR maxActivated = specificRecords.stream()
                    .filter(record -> STATUS_ACTIVATED.equalsIgnoreCase(record.getStatus()))
                    .max(Comparator.comparing(CertCertificateVsR::getVersion))
                    .orElse(null);

            CertCertificateVsR maxCanary = specificRecords.stream()
                    .filter(record -> STATUS_CANARY.equalsIgnoreCase(record.getStatus()))
                    .max(Comparator.comparing(CertCertificateVsR::getVersion))
                    .orElse(null);

            if (maxActivated != null) {
                temp.put(STATUS_ACTIVATED, maxActivated.getCertId());
            }
            if (maxCanary != null && (maxActivated == null || maxCanary.getVersion() > maxActivated.getVersion())) {
                temp.put(STATUS_CANARY, maxCanary.getCertId());
            }
            results.put(vsId, temp);
        });

        return results;
    }

    @Override
    public void installDefault(Long certId, List<String> ips, final boolean overwriteIfExist) throws Exception {
        certInstallClient.installDefault(C.toCertCertificateWithBlobs(getByCertId(certId, true)), ips, overwriteIfExist);
    }

    @Override
    public void remoteInstall(final Long vsId, List<String> ips, final Certificate certificate, boolean overwriteIfExist) throws Exception {
        certInstallClient.install(vsId, ips, C.toCertCertificateWithBlobs(certificate), overwriteIfExist);
    }

    @Override
    public void remoteInstall(Long slbId, List<String> ips, boolean overwrite) throws Exception {
        if (slbId == null || slbId.equals(0L) || ips.size() == 0) {
            return;
        }

        // find vses by slbid
        Set<IdVersion> searchKey = virtualServerCriteriaQuery.queryBySlbId(slbId);
        Set<Long> next = new HashSet<>();
        for (IdVersion sk : searchKey) {
            next.add(sk.getId());
        }
        next.retainAll(virtualServerCriteriaQuery.queryBySsl(true));
        if (next.size() == 0) {
            return;
        }

        Set<IdVersion> searchKeys = virtualServerCriteriaQuery.queryByIdsAndMode(next.toArray(new Long[next.size()]), SelectionMode.ONLINE_FIRST);
        Map<Long, String> rVsDomain = new HashMap<>();
        for (VirtualServer vs : virtualServerRepository.listAll(searchKeys.toArray(new IdVersion[searchKeys.size()]))) {
            List<String> domains = vs.getDomains().stream().map(Domain::getName).collect(Collectors.toList());
            String compoundDomain = Joiner.on("|").join(domains);
            compoundDomain = CertUtil.standardizeDomain(compoundDomain);

            rVsDomain.put(vs.getId(), compoundDomain);
        }

        List<Certificate> certificates = getByExactDomains(rVsDomain.values().toArray(new String[rVsDomain.size()]), true);
        List<CertCertificateWithBLOBs> certCertificateWithBLOBs = new ArrayList<>(certificates.size());
        certificates.forEach(certificate -> certCertificateWithBLOBs.add(C.toCertCertificateWithBlobs(certificate)));
        certInstallClient.install(slbId, certCertificateWithBLOBs, rVsDomain, ips, overwrite);
    }

    @Override
    public void uninstall(Long vsId, List<String> ips) throws Exception {
        certInstallClient.uninstall(vsId, ips);
    }

    @Override
    public int insertCertSlbServerOrUpdateCert(CertCertificateSlbServerR record) {
        return certificateSlbServerRMapper.insertCertSlbServerOrUpdateCert(record);
    }

    public CertCertificateVsR maxVersionForCertVs(Long vsId) {
        CertCertificateVsRExample example = new CertCertificateVsRExample();
        example.or().andVsIdEqualTo(vsId);
        example.setOrderByClause("version DESC");
        return certVsRMapper.selectOneByExample(example);
    }

    /*
     * @Description: find online and offline slb servers of the online-version and offline-version vs specified by vsId
     * @return
     **/
    private Map<Long, Set<String>> batchGetSlbServers(List<Long> vsIds) throws Exception {
        if (vsIds == null || vsIds.size() == 0) {
            return new HashMap<>();
        }
        Map<Long, Set<String>> serversByVs = new HashMap<>(vsIds.size());

        Set<IdVersion> vsKeys = virtualServerCriteriaQuery.queryByIdsAndMode(vsIds.toArray(new Long[0]), SelectionMode.REDUNDANT);
        if (vsKeys == null || vsKeys.size() == 0) {
            throw new ValidationException("Virtual server with ids " + vsIds + " does not exist.");
        }
        Map<Long, Set<Long>> slbsByVs = slbCriteriaQuery.batchQueryByVses(vsKeys.toArray(new IdVersion[0]));
        Set<Long> slbIds = new HashSet<>();
        slbsByVs.values().forEach(slbIds::addAll);

        Set<IdVersion> slbKeys = slbCriteriaQuery.queryByIdsAndMode(slbIds.toArray(new Long[0]), SelectionMode.REDUNDANT);
        List<Slb> slbs = slbRepository.list(slbKeys.toArray(new IdVersion[0]));
        Map<Long, Slb> slbById = new HashMap<>(slbIds.size());
        for (Slb slb : slbs) {
            slbById.put(slb.getId(), slb);
        }

        slbsByVs.forEach((vsId, specificSlbIds) -> {
            Set<String> servers = new HashSet<>();
            specificSlbIds.forEach(id -> {
                List<SlbServer> slbServers = slbById.get(id).getSlbServers();
                servers.addAll(slbServers.stream().map(SlbServer::getIp).collect(Collectors.toList()));
            });
            serversByVs.put(vsId, servers);
        });

        return serversByVs;
    }

    private Set<String> getSlbServers(Long vsId) throws Exception {
        Map<Long, Set<String>> serversByVs = batchGetSlbServers(Collections.singletonList(vsId));
        return serversByVs.containsKey(vsId) ? serversByVs.get(vsId) : new HashSet<>();
    }

    protected class CertTaskResponse {
        boolean success;
        String ip;

        CertTaskResponse(boolean success, String ip) {
            this.success = success;
            this.ip = ip;
        }

        public boolean getSuccess() {
            return success;
        }
    }

    protected class CertManageTask extends FutureTask<CertTaskResponse> {

        public CertManageTask(final String ip, final Object[] args, final CertClientOperation op) {
            this(new Callable<CertTaskResponse>() {
                @Override
                public CertTaskResponse call() throws Exception {
                    CertSyncClient c = new CertSyncClient("http://" + ip + ":8099");
                    Response res;
                    try {
                        res = op.call(c, args);
                    } catch (Exception ex) {
                        logger.error("Fail to send out install certificate request to " + ip + ".", ex);
                        return new CertTaskResponse(false, ip);
                    }

                    if (res.getStatus() / 100 == 2) {
                        return new CertTaskResponse(true, ip);
                    } else {
                        try {
                            String responseEntity = IOUtils.inputStreamStringify((InputStream) res.getEntity());
                            logger.error("Fail to install certificate on " + ip + ". " + responseEntity);
                        } catch (IOException ex) {
                            logger.error("Fail to install certificate on " + ip + ". An unexpected error occurred when stringifying response.", ex);
                        }
                        return new CertTaskResponse(false, ip);
                    }
                }
            });
        }

        CertManageTask(Callable<CertTaskResponse> callable) {
            super(callable);
        }
    }

    public static class CertSyncClient extends AbstractRestClient {
        public CertSyncClient(String url) {
            super(url);
        }

        Response requestInstallDefault(Long certId, boolean force) {
            return getTarget().path("/api/cert/default/localInstall").queryParam("certId", certId).queryParam("force", force).request().headers(getDefaultHeaders()).get();
        }

        Response requestInstallDefault(Certificate certificate, boolean force) {
            return getTarget().
                    queryParam("force", force).
                    path("/api/cert/default/localInstall").
                    request(MediaType.APPLICATION_JSON).
                    headers(getDefaultHeaders()).
                    post(Entity.entity(certificate, MediaType.APPLICATION_JSON), Response.class);
        }

        Response requestInstall(Long vsId, Certificate certificate) {
            return getTarget().
                    queryParam("vsId", vsId).
                    path("/api/cert/localInstall").
                    request(MediaType.APPLICATION_JSON).
                    headers(getDefaultHeaders()).
                    post(Entity.entity(certificate, MediaType.APPLICATION_JSON), Response.class);
        }

        public HashMap<Long, CertCertificateWithBLOBs> requestCerts(Set<Long> vsIds) {
            WebTarget target = getTarget().path("/api/cert/find");
            if (vsIds == null) return null;
            ArrayList<Long> vsIdsArray = new ArrayList<>(vsIds);
            for (int i = 0; i < vsIdsArray.size(); i++) {
                target = target.queryParam("vsIds", vsIdsArray.get(i));
            }
            String responseStr = target.request().headers(getDefaultHeaders()).get(String.class);

            HashMap<Long, CertCertificateWithBLOBs> result = ObjectJsonParser.parse(responseStr,
                    new com.fasterxml.jackson.core.type.TypeReference<HashMap<Long, CertCertificateWithBLOBs>>() {
                    });

            return result;
        }

        public CertCertificateWithBLOBs getDefaultCertificate(Long slbId) {
            Response response = getTarget().path("/api/cert/query/default").queryParam("slbId", slbId).request().headers(getDefaultHeaders()).get();
            InputStream inputStream = (InputStream) response.getEntity();
            return ObjectJsonParser.parse(inputStream, CertCertificateWithBLOBs.class);
        }

        public Map<Long, CertCertificateWithBLOBs> getCertsBySlbId(Long slbId) throws Exception {
            Response response = getTarget().path("/api/cert/queryBySlb").queryParam("slbId", slbId).request().headers(getDefaultHeaders()).get();
            InputStream inputStream = (InputStream) response.getEntity();
            return ObjectJsonParser.parse(
                    IOUtils.inputStreamStringify(inputStream),
                    new TypeReference<HashMap<Long, CertCertificateWithBLOBs>>() {
                    });
        }

        Response setCertificateServerStatus(Long vsId, Long certId) {
            return getTarget().queryParam("vsId", vsId).queryParam("certId", certId).path("/api/cert/set/status").request().headers(getDefaultHeaders()).get();
        }

        Response requestUninstall(Long vsId) {
            return getTarget().path("/api/cert/localUninstall").queryParam("vsId", vsId).request().headers(getDefaultHeaders()).get();
        }

        Response requestBatchInstall(Set<Long> vsIds, Map<Long, String> rVsDomainMap, List<Certificate> certificates, boolean force) {
            Map<String, Object> map = new HashMap<>();
            map.put("vsIds", vsIds);
            map.put("rVsDomain", rVsDomainMap);
            map.put("certificates", certificates);

            return getTarget().
                    queryParam("force", force).
                    path("/api/cert/localBatchInstall").
                    request(MediaType.APPLICATION_JSON).
                    headers(getDefaultHeaders()).
                    post(Entity.entity(map, MediaType.APPLICATION_JSON), Response.class);
        }
    }

    protected interface CertClientOperation {
        Response call(CertSyncClient c, Object[] args);
    }

    @Override
    public Long upgrade(InputStream cert, InputStream key, String domain, boolean state) throws Exception {
        return null;
    }

    @Override
    public Long getCertificateOnBoard(String domain) throws Exception {
        return getMaxCertIdByDomain(domain);
    }

    @Override
    public Long loadCertificate(String domain, String cid, Long vsId) throws Exception {
        return null;
    }

    @Override
    public Long activateCertificate(Long vsId, String cid, boolean force) throws Exception {
        return null;
    }

    @Override
    public Long getCertIdByCid(String cid) throws Exception {
        return null;
    }

    @Override
    public Map<Long, Map<String, String>> findCID(Long[] vsIds, boolean withCert) throws Exception {
        return new HashMap<>();
    }

    @Override
    public String getActivatedCId(Long vsId) throws Exception {
        return null;
    }

    @Override
    public void cleanCert(Long certId) throws Exception {
        if (certId == null) {
            return;
        }

        CertCertificateSlbServerRExample example = new CertCertificateSlbServerRExample();
        example.or().andCertIdEqualTo(certId);
        certificateSlbServerRMapper.deleteByExample(example);
        deleteCertByCertId(certId);
    }

    @Override
    public void deleteCertByCertId(Long certId) throws Exception {
        certificateResourceService.deleteByPrimaryKey(certId);
    }

    @Override
    public List<Certificate> getCertByDomains(String[] domains, boolean state) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<String> canaryCertificate(Long vsId, String cid, double rate) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<String> canaryIps(Long vsId, String cid) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public Long upload(InputStream cert, InputStream key, String domain, boolean state, String cid) throws Exception {
        return null;
    }

    @Override
    public String getCidByCertId(Long certId) {
        return null;
    }
}
