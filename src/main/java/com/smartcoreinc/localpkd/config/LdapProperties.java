package com.smartcoreinc.localpkd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ldap")
public class LdapProperties {
    private EndPoint read = new EndPoint();
    private EndPoint write = new EndPoint();
    private Bind bind = new Bind();

    public EndPoint getRead() {
        return read;
    }

    public void setRead(EndPoint read) {
        this.read = read;
    }

    public EndPoint getWrite() {
        return write;
    }

    public void setWrite(EndPoint write) {
        this.write = write;
    }

    public Bind getBind() {
        return bind;
    }

    public void setBind(Bind bind) {
        this.bind = bind;
    }

    public static class EndPoint {
        private String host;
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Bind {
        private String dn;
        private String password;

        public String getDn() {
            return dn;
        }

        public void setDn(String dn) {
            this.dn = dn;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
