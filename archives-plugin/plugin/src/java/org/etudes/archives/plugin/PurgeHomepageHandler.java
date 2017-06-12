/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeHomepageHandler.java $
 * $Id: PurgeHomepageHandler.java 8245 2014-06-12 22:44:18Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2014 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.archives.plugin;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeHandler;
import org.etudes.homepage.api.HomePageService;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResourcesService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.exception.PermissionException;

/**
 * Archives PurgeHandler for Homepage
 */
public class PurgeHomepageHandler implements PurgeHandler
{
	/** The application Id. */
	protected final static String applicationId = "e3.homepage";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeHomepageHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterPurgeHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerPurgeHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void purge(String siteId)
	{
		M_log.info("purge " + applicationId + " in site: " + siteId);

		try
		{
			homePageService().purge("admin", siteId);

			// this leaves the site resources
			List<SitePlacement> placements = siteResourcesService().getSitePlacements(siteId);
			for (SitePlacement placement : placements)
			{
				siteResourcesService().removeSitePlacement(placement);
			}
		}
		catch (PermissionException e)
		{
			M_log.warn("purge: " + e);
		}
	}

	/**
	 * Set the archives service.
	 * 
	 * @param service
	 *        The archives service.
	 */
	public void setArchivesService(ArchivesService service)
	{
		this.archivesService = service;
	}

	/**
	 * @return The HomePageService, via the component manager.
	 */
	private HomePageService homePageService()
	{
		return (HomePageService) ComponentManager.get(HomePageService.class);
	}

	/**
	 * @return The SiteResourcesService, via the component manager.
	 */
	private SiteResourcesService siteResourcesService()
	{
		return (SiteResourcesService) ComponentManager.get(SiteResourcesService.class);
	}
}
