package com.ctrip.zeus.model.queue;

public class PolicyData {
    private Long m_id;

    private String m_name;

    private Integer m_version;

    public PolicyData() {
    }

    protected boolean equals(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        } else if (o2 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }



    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PolicyData) {
            PolicyData _o = (PolicyData) obj;

            if (!equals(m_id, _o.getId())) {
                return false;
            }

            if (!equals(m_name, _o.getName())) {
                return false;
            }

            if (!equals(m_version, _o.getVersion())) {
                return false;
            }


            return true;
        }

        return false;
    }

    public Long getId() {
        return m_id;
    }

    public String getName() {
        return m_name;
    }

    public Integer getVersion() {
        return m_version;
    }

    @Override
    public int hashCode() {
        int hash = 0;

        hash = hash * 31 + (m_id == null ? 0 : m_id.hashCode());
        hash = hash * 31 + (m_name == null ? 0 : m_name.hashCode());
        hash = hash * 31 + (m_version == null ? 0 : m_version.hashCode());

        return hash;
    }



    public PolicyData setId(Long id) {
        m_id = id;
        return this;
    }

    public PolicyData setName(String name) {
        m_name = name;
        return this;
    }

    public PolicyData setVersion(Integer version) {
        m_version = version;
        return this;
    }

}
