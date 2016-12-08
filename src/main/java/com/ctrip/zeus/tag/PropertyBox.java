package com.ctrip.zeus.tag;

/**
 * Created by zhoumy on 2015/7/16.
 */
public interface PropertyBox {

    void removeProperty(String pname, boolean force) throws Exception;

    void renameProperty(String originPname, String updatedPname) throws Exception;

    boolean set(String pname, String pvalue, String type, Long itemId) throws Exception;

    void set(String pname, String pvalue, String type, Long[] itemId) throws Exception;

    boolean clear(String type, Long itemId) throws Exception;

    boolean clear(String pname, String pvalue, String type, Long itemId) throws Exception;

    void clear(String pname, String pvalue, String type, Long[] itemId) throws Exception;
}
