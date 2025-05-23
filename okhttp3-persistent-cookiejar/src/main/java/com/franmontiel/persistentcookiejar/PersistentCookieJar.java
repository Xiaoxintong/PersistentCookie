/*
 * Copyright (C) 2016 Francisco José Montiel Navarro.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.franmontiel.persistentcookiejar;

import com.franmontiel.persistentcookiejar.cache.CookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class PersistentCookieJar implements ClearableCookieJar {

    private CookieCache cache;
    private CookiePersistor persistor;

    public PersistentCookieJar(CookieCache cache, CookiePersistor persistor) {
        this.cache = cache;
        this.persistor = persistor;

        //XXT 修改
//        this.cache.addAll(persistor.loadAll());
        syncCookieFromPersistor();
    }

    @Override
    synchronized public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        List<Cookie> filterCookies = filterLoginCookies(url,cookies);
        List<Cookie> copyCookies = copyLoginCookies(url, filterCookies);

        cache.addAll(copyCookies);
        //XXT修改
//        persistor.saveAll(filterPersistentCookies(cookies));
        persistor.saveAll(copyCookies);
    }

    private List<Cookie> filterPersistentCookies(List<Cookie> cookies) {
        List<Cookie> persistentCookies = new ArrayList<>();

        for (Cookie cookie : cookies) {
            if (cookie.persistent()) {
                persistentCookies.add(cookie);
            }
        }
        return persistentCookies;
    }

    private List<Cookie> copyLoginCookies(HttpUrl url, List<Cookie> cookies) {
        List<Cookie> tempCookies = new ArrayList<>();
        tempCookies.addAll(cookies);

        for (Cookie cookie : cookies) {
            String cookieName = cookie.name();
            if (cookieName!=null && (cookieName.equals("XXT_TICKET")
                    || cookieName.equals("XXT_ID")
                    || cookieName.equals("_XSID_")
                    || cookieName.equals("_SSO_STATE_TICKET"))) {

                String domain = cookie.domain();

                if (domain==null || domain.length()==0 || !domain.endsWith("xxt.cn")) {
                    Cookie newXxtCookie = new Cookie.Builder()
                            .name(cookie.name())
                            .value(cookie.value())
                            .domain("xxt.cn")
                            .path(cookie.path())
                            .build();
                    tempCookies.add(newXxtCookie);
                }

                if (domain==null || domain.length()==0 || !domain.endsWith("hbjxt.cn")) {
                    Cookie newJxtCookie = new Cookie.Builder()
                            .name(cookie.name())
                            .value(cookie.value())
                            .domain("hbjxt.cn")
                            .path(cookie.path())
                            .build();
                    tempCookies.add(newJxtCookie);
                }

                if (domain==null || domain.length()==0 || !domain.endsWith("lexue.cn")) {
                    Cookie newLexueCookie = new Cookie.Builder()
                            .name(cookie.name())
                            .value(cookie.value())
                            .domain("lexue.cn")
                            .path(cookie.path())
                            .build();
                    tempCookies.add(newLexueCookie);
                }

                if (domain==null || domain.length()==0 || !domain.endsWith("xinzx.cn")) {
                    Cookie newXinzxCookie = new Cookie.Builder()
                            .name(cookie.name())
                            .value(cookie.value())
                            .domain("xinzx.cn")
                            .path(cookie.path())
                            .build();
                    tempCookies.add(newXinzxCookie);
                }
            }
        }


        return tempCookies;
    }

    /**
     * 对于 XXT_TICKET XXT_ID _XSID_ _SSO_STATE_TICKET 只允许Login工程修改，以免被其它请求因为多线程误伤
     * @param cookies 返回的cookie
     * @return 处理后的cookie
     */
    private List<Cookie> filterLoginCookies(HttpUrl url, List<Cookie> cookies) {
        if (url.host()!=null
                && ("login.xxt.cn".equals(url.host())
                || "login.hbjxt.cn".equals(url.host())
                || "login.lexue.cn".equals(url.host())
                || "ai.xxt.cn".equals(url.host())
                || url.toString().contains("rest.xxt.cn/login"))) {
            return cookies;
        } else {
            List<Cookie> noLoginCookies = new ArrayList<>();

            for (Cookie cookie : cookies) {
                String cookieName = cookie.name();
                if (cookieName!=null
                        && !cookieName.equals("XXT_TICKET")
                        && !cookieName.equals("XXT_ID")
                        && !cookieName.equals("_XSID_")
                        && !cookieName.equals("_SSO_STATE_TICKET")) {
                    noLoginCookies.add(cookie);
                }
            }
            return noLoginCookies;
        }
    }

    private static List<Cookie> filterExpiredCookies(List<Cookie> cookies) {
        List<Cookie> noExpiredCookies = new ArrayList<>();

        for (Cookie cookie : cookies) {
            if (!isCookieExpired(cookie)) {
                noExpiredCookies.add(cookie);
            }
        }
        return noExpiredCookies;
    }

    //XXT修改
    /**
     * 从sp中同步cookie
     */
    synchronized private void syncCookieFromPersistor() {
        List<Cookie> persistorCookieList = persistor.loadAll();
        if (persistorCookieList!=null && persistorCookieList.size()>0) {
            this.cache.addAll(persistorCookieList);
        }
    }

    @Override
    synchronized public List<Cookie> loadForRequest(HttpUrl url) {

        //XXT 添加
        syncCookieFromPersistor();

        List<Cookie> cookiesToRemove = new ArrayList<>();
        List<Cookie> validCookies = new ArrayList<>();

        for (Iterator<Cookie> it = cache.iterator(); it.hasNext(); ) {
            Cookie currentCookie = it.next();

            if (isCookieExpired(currentCookie)) {
                cookiesToRemove.add(currentCookie);
                it.remove();
            } else if (currentCookie.matches(url)) {
                validCookies.add(currentCookie);
            }
        }

        persistor.removeAll(cookiesToRemove);

        return validCookies;
    }

    private static boolean isCookieExpired(Cookie cookie) {
        return cookie.expiresAt() < System.currentTimeMillis();
    }

    @Override
    synchronized public void clearSession() {
        cache.clear();
        cache.addAll(persistor.loadAll());
    }

    @Override
    synchronized public void clear() {
        cache.clear();
        persistor.clear();
    }

    @Override
    synchronized public boolean isNull() {
        return cache==null || cache.isNull() || persistor==null || persistor.isNull();
    }
}
