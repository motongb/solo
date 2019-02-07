/*
 * Solo - A small and beautiful blogging system written in Java.
 * Copyright (c) 2010-2019, b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.solo.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.servlet.HttpMethod;
import org.b3log.latke.servlet.RequestContext;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Paginator;
import org.b3log.solo.SoloServletListener;
import org.b3log.solo.model.Common;
import org.b3log.solo.model.Option;
import org.b3log.solo.model.Skin;
import org.b3log.solo.processor.console.ConsoleRenderer;
import org.b3log.solo.service.DataModelService;
import org.b3log.solo.service.InitService;
import org.b3log.solo.service.PreferenceQueryService;
import org.b3log.solo.service.StatisticMgmtService;
import org.b3log.solo.util.Skins;
import org.b3log.solo.util.Solos;
import org.json.JSONObject;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.Map;

/**
 * Index processor.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://hacpai.com/member/DASHU">DASHU</a>
 * @version 1.2.4.14, Feb 7, 2019
 * @since 0.3.1
 */
@RequestProcessor
public class IndexProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(IndexProcessor.class);

    /**
     * DataModelService.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Preference query service.
     */
    @Inject
    private PreferenceQueryService preferenceQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Statistic management service.
     */
    @Inject
    private StatisticMgmtService statisticMgmtService;

    /**
     * Initialization service.
     */
    @Inject
    private InitService initService;

    /**
     * Shows index with the specified context.
     *
     * @param context the specified context
     * @throws Exception exception
     */
    @RequestProcessing(value = {"", "/"}, method = HttpMethod.GET)
    public void showIndex(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        try {
            final int currentPageNum = Paginator.getPage(request);
            final JSONObject preference = preferenceQueryService.getPreference();

            // 前台皮肤切换 https://github.com/b3log/solo/issues/12060
            String specifiedSkin = Skins.getSkinDirName(context);
            if (StringUtils.isBlank(specifiedSkin)) {
                specifiedSkin = preference.optString(Option.ID_C_SKIN_DIR_NAME);
            }
            request.setAttribute(Keys.TEMAPLTE_DIR_NAME, specifiedSkin);

            final Cookie cookie = new Cookie(Skin.SKIN, specifiedSkin);
            cookie.setMaxAge(60 * 60); // 1 hour
            cookie.setPath("/");
            response.addCookie(cookie);

            Skins.fillLangs(preference.optString(Option.ID_C_LOCALE_STRING), (String) context.attr(Keys.TEMAPLTE_DIR_NAME), dataModel);

            dataModelService.fillIndexArticles(context, dataModel, currentPageNum, preference);
            dataModelService.fillCommon(context, dataModel, preference);

            dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, currentPageNum);
            final int previousPageNum = currentPageNum > 1 ? currentPageNum - 1 : 0;
            dataModel.put(Pagination.PAGINATION_PREVIOUS_PAGE_NUM, previousPageNum);

            final Integer pageCount = (Integer) dataModel.get(Pagination.PAGINATION_PAGE_COUNT);
            final int nextPageNum = currentPageNum + 1 > pageCount ? pageCount : currentPageNum + 1;
            dataModel.put(Pagination.PAGINATION_NEXT_PAGE_NUM, nextPageNum);
            dataModel.put(Common.PATH, "");

            statisticMgmtService.incBlogViewCount(context, response);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            context.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Shows kill browser page with the specified context.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/kill-browser", method = HttpMethod.GET)
    public void showKillBrowser(final RequestContext context) {
        final HttpServletRequest request = context.getRequest();
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "kill-browser.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        try {
            final Map<String, String> langs = langPropsService.getAll(Locales.getLocale(request));
            dataModel.putAll(langs);
            final JSONObject preference = preferenceQueryService.getPreference();
            dataModelService.fillCommon(context, dataModel, preference);
            Keys.fillServer(dataModel);
            Keys.fillRuntime(dataModel);
            dataModelService.fillMinified(dataModel);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            context.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Shows start page.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/start", method = HttpMethod.GET)
    public void showStart(final RequestContext context) {
        String destinationURL = context.param(Common.GOTO);
        if (StringUtils.isBlank(destinationURL)) {
            destinationURL = Latkes.getServePath() + Common.ADMIN_INDEX_URI;
        } else if (!isInternalLinks(destinationURL)) {
            destinationURL = Latkes.getServePath();
        }

        if (null != Solos.getCurrentUser(context.getRequest(), context.getResponse())) {
            context.sendRedirect(destinationURL);

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new ConsoleRenderer();
        final Map<String, Object> dataModel = renderer.getDataModel();
        final HttpServletRequest request = context.getRequest();
        renderer.setTemplateName("start.ftl");
        context.setRenderer(renderer);
        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale(request));
        dataModel.putAll(langs);
        dataModel.put(Common.VERSION, SoloServletListener.VERSION);
        dataModel.put(Common.STATIC_RESOURCE_VERSION, Latkes.getStaticResourceVersion());
        dataModel.put(Common.YEAR, String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        Keys.fillRuntime(dataModel);
        dataModelService.fillMinified(dataModel);

        Solos.addGoogleNoIndex(context);
    }

    /**
     * Logout.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/logout", method = HttpMethod.GET)
    public void logout(final RequestContext context) {
        final HttpServletRequest httpServletRequest = context.getRequest();

        Solos.logout(httpServletRequest, context.getResponse());

        String destinationURL = context.param(Common.GOTO);
        if (StringUtils.isBlank(destinationURL) || !isInternalLinks(destinationURL)) {
            destinationURL = Latkes.getServePath();
        }

        context.sendRedirect(destinationURL);
        Solos.addGoogleNoIndex(context);
    }

    /**
     * Preventing unvalidated redirects and forwards. See more at:
     * <a href="https://www.owasp.org/index.php/Unvalidated_Redirects_and_Forwards_Cheat_Sheet">https://www.owasp.org/index.php/
     * Unvalidated_Redirects_and_Forwards_Cheat_Sheet</a>.
     *
     * @return whether the destinationURL is an internal link
     */
    private boolean isInternalLinks(String destinationURL) {
        return destinationURL.startsWith(Latkes.getServePath());
    }
}
