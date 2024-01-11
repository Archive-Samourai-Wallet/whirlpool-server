package com.samourai.whirlpool.server.config.security;

import com.samourai.javaserver.config.ServerServicesConfig;
import com.samourai.javawsserver.config.JWSSConfig;
import com.samourai.whirlpool.protocol.v0.WhirlpoolEndpointV0;
import com.samourai.whirlpool.server.controllers.rest.DexConfigController;
import com.samourai.whirlpool.server.controllers.rest.SystemController;
import com.samourai.whirlpool.server.controllers.web.*;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
  private static final String[] WEB_ACTUATOR_ENDPOINTS = new String[] {"/actuator/prometheus"};

  private static final String[] WEB_ADMIN_ENDPOINTS =
      new String[] {
        StatusWebController.ENDPOINT,
        HistoryWebController.ENDPOINT,
        ClientsWebController.ENDPOINT,
        ConfigWebController.ENDPOINT,
        BanWebController.ENDPOINT,
        SystemWebController.ENDPOINT,
        SorobanWebController.ENDPOINT,
        MetricsWebController.ENDPOINT_APP,
        MetricsWebController.ENDPOINT_SYSTEM
      };

  private static final String[] REST_MIX_ENDPOINTS =
      new String[] {
        WhirlpoolEndpointV0.REST_POOLS,
        WhirlpoolEndpointV0.REST_CHECK_OUTPUT,
        WhirlpoolEndpointV0.REST_REGISTER_OUTPUT,
        WhirlpoolEndpointV0.REST_TX0_DATA_V0,
        WhirlpoolEndpointV0.REST_TX0_DATA_V1,
        WhirlpoolEndpointV0.REST_TX0_PUSH,
        WhirlpoolEndpointV0.REST_PREFIX + "tx0Notify", // @deprecated
        SystemController.ENDPOINT_HEALTH,
        DexConfigController.ENDPOINT_DEXCONFIG
      };

  private JWSSConfig config;

  @Autowired
  public WebSecurityConfig(JWSSConfig config) {
    this.config = config;
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    String WS_CONNECT_XHR = WhirlpoolEndpointV0.WS_CONNECT + "/**";

    // disable csrf for our endpoints
    http.csrf()
        .ignoringAntMatchers(ArrayUtils.addAll(REST_MIX_ENDPOINTS, WS_CONNECT_XHR))
        .and()
        .authorizeRequests()

        // public statics
        .antMatchers(ServerServicesConfig.STATICS)
        .permitAll()

        // public login form
        .antMatchers(LoginWebController.ENDPOINT)
        .permitAll()
        .antMatchers(LoginWebController.PROCESS_ENDPOINT)
        .permitAll()

        // public actuator
        .antMatchers(WEB_ACTUATOR_ENDPOINTS)
        .permitAll()

        // public mixing websocket
        .antMatchers(ArrayUtils.addAll(config.getWebsocketEndpoints(), WS_CONNECT_XHR))
        .permitAll()
        .antMatchers(REST_MIX_ENDPOINTS)
        .permitAll()

        // restrict admin
        .antMatchers(WEB_ADMIN_ENDPOINTS)
        .hasAnyAuthority(WhirlpoolPrivilege.ALL.toString())

        // reject others
        .anyRequest()
        .denyAll()
        .and()

        // custom login form
        .formLogin()
        .loginProcessingUrl(LoginWebController.PROCESS_ENDPOINT)
        .loginPage(LoginWebController.ENDPOINT)
        .defaultSuccessUrl(StatusWebController.ENDPOINT, true);
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider(
      WhirlpoolUserDetailsService whirlpoolUserDetailsService) {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(whirlpoolUserDetailsService);
    authProvider.setPasswordEncoder(encoder());
    return authProvider;
  }

  @Bean
  public PasswordEncoder encoder() {
    return new BCryptPasswordEncoder(11);
  }
}
