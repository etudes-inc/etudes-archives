/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveWebContentHandler.java $
 * $Id: ArchiveWebContentHandler.java 8752 2014-09-11 17:35:56Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014 Etudes, Inc.
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

import java.util.Collection;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;

/**
 * Archives archive handler for web content tools in a site
 */
public class ArchiveWebContentHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveWebContentHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.iframe";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		try
		{
			Site site = this.siteService.getSite(siteId);

			// web content - information is in the site's tools
			Collection<ToolConfiguration> webContentTools = site.getTools("sakai.iframe");
			for (ToolConfiguration tool : webContentTools)
			{
				Properties toolProps = tool.getConfig();
				String thirdPartyService = toolProps.getProperty("thirdPartyService");
				
				//skip if external service
				if ("Yes".equalsIgnoreCase(thirdPartyService)) continue;
				
				// make an artifact
				Artifact webContentArtifact = archive.newArtifact(applicationId, "/sakai.iframe");

				String title = tool.getTitle();
				String height = toolProps.getProperty("height");
				String source = toolProps.getProperty("source");
				String key = toolProps.getProperty("key");
				String secret = toolProps.getProperty("secret");
				String extraInformation = toolProps.getProperty("extraInformation");
				Boolean newPage = site.getPage(tool.getPageId()).isPopUp();
				String pageTitle = site.getPage(tool.getPageId()).getTitle();

				webContentArtifact.getProperties().put("toolTitle", title);
				webContentArtifact.getProperties().put("height", height);
				webContentArtifact.getProperties().put("source", source);
				if (key != null) webContentArtifact.getProperties().put("key", key);
				if (secret != null) webContentArtifact.getProperties().put("secret", secret);
				if (extraInformation != null) webContentArtifact.getProperties().put("extraInformation", extraInformation);
				webContentArtifact.getProperties().put("thirdPartyService", "No");
				
				webContentArtifact.getProperties().put("newPage", newPage);
				webContentArtifact.getProperties().put("pageTitle", pageTitle);

				// archive it
				archive.archive(webContentArtifact);
			}
		}
		catch (IdUnusedException e)
		{
		}
	}

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterArchiveHandler(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public String getApplicationId()
	{
		return applicationId;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerArchiveHandler(this);
		M_log.info("init()");
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
	 * Dependency: SiteService.
	 * 
	 * @param service
	 *        The SiteService.
	 */
	public void setSiteService(SiteService service)
	{
		this.siteService = service;
	}
}
