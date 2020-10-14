package com.ctrip.zeus.dao.mapper;

import com.ctrip.zeus.dao.entity.WafData;
import com.ctrip.zeus.dao.entity.WafDataExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WafDataMapper {
    long countByExample(WafDataExample example);

    int deleteByExample(WafDataExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WafData record);

    int insertSelective(WafData record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    WafData selectOneByExample(WafDataExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    WafData selectOneByExampleSelective(@Param("example") WafDataExample example, @Param("selective") WafData.Column ... selective);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    WafData selectOneByExampleWithBLOBs(WafDataExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    List<WafData> selectByExampleSelective(@Param("example") WafDataExample example, @Param("selective") WafData.Column ... selective);

    List<WafData> selectByExampleWithBLOBs(WafDataExample example);

    List<WafData> selectByExample(WafDataExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    WafData selectByPrimaryKeySelective(@Param("id") Long id, @Param("selective") WafData.Column ... selective);

    WafData selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") WafData record, @Param("example") WafDataExample example);

    int updateByExampleWithBLOBs(@Param("record") WafData record, @Param("example") WafDataExample example);

    int updateByExample(@Param("record") WafData record, @Param("example") WafDataExample example);

    int updateByPrimaryKeySelective(WafData record);

    int updateByPrimaryKeyWithBLOBs(WafData record);

    int updateByPrimaryKey(WafData record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int upsert(WafData record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int upsertSelective(WafData record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table waf_data
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int upsertWithBLOBs(WafData record);
}