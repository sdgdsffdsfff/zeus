package com.ctrip.zeus.service.model;

import org.unidal.dal.jdbc.DalException;

/**
 * @author:xingchaowang
 * @date: 3/7/2015.
 */
public interface DbClean {
    void deleteSlbVip(long id) throws DalException;

    void deleteSlbServer(long id) throws DalException;

    void deleteSlbVirtualServer(long id) throws DalException;

    void deleteSlbDomain(long id) throws DalException;

    void deleteAppSlb(long id) throws DalException;

    void deleteAppServer(long id) throws DalException;
}