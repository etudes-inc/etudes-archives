/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveArchiveHandler.java $
 * $Id: ArchiveArchiveHandler.java 8752 2014-09-11 17:35:56Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012 Etudes, Inc.
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.DateHelper;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.Time;

/**
 * Archives archive handler for general Archive information
 */
public class ArchiveArchiveHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveArchiveHandler.class);

	/** The application Id. */
	protected final static String applicationId = "etudes.archives";

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

		// make an artifact
		Artifact artifact = archive.newArtifact(applicationId, "/archive");

		// set the archive information
		artifact.getProperties().put("siteId", siteId);
		artifact.getProperties().put("date", Long.valueOf(new Date().getTime()));
		try
		{
			Site site = this.siteService.getSite(siteId);
			artifact.getProperties().put("siteTitle", site.getTitle());
			artifact.getProperties().put("siteDescription", site.getDescription());
			artifact.getProperties().put("siteShortDescription", site.getShortDescription());
			artifact.getProperties().put("siteType", site.getType());

			// publication dates
			try
			{
				// These properties should be Time properties, but they were initially stored in the data entry format, default time zone -ggolden
				String pubDateValue = site.getProperties().getProperty("pub-date");
				Date pubDate = DateHelper.parseDateFromDefault(pubDateValue);
				if (pubDate != null) artifact.getProperties().put("pubDate", Long.valueOf(pubDate.getTime()));
			}
			catch (ParseException e)
			{
			}
			try
			{
				// These properties should be Time properties, but they were initially stored in the data entry format, default time zone -ggolden
				String unpubDateValue = site.getProperties().getProperty("unpub-date");
				Date unpubDate = DateHelper.parseDateFromDefault(unpubDateValue);
				if (unpubDate != null) artifact.getProperties().put("unpubDate", Long.valueOf(unpubDate.getTime()));
			}
			catch (ParseException e)
			{
			}

			// skin and info
			if (site.getIconUrl() != null) artifact.getProperties().put("iconUrl", site.getIconUrl());
			if (site.getInfoUrl() != null) artifact.getProperties().put("infoUrl", site.getInfoUrl());
			if (site.getSkin() != null) artifact.getProperties().put("skin", site.getSkin());

			// get any groups and sections (title and description)
			List<Map<String, Object>> groupsCollection = new ArrayList<Map<String, Object>>();
			List<Map<String, Object>> sectionsCollection = new ArrayList<Map<String, Object>>();
			List<Map<String, Object>> externalServiceCollection = new ArrayList<Map<String, Object>>();
			
			Collection<Group> groups = site.getGroups();
			for (Group group : groups)
			{
				// for groups
				if (group.getProperties().getProperty("sections_category") == null)
				{
					Map<String, Object> groupMap = new HashMap<String, Object>();
					groupsCollection.add(groupMap);

					groupMap.put("title", group.getTitle());
					if (group.getDescription() != null) groupMap.put("description", group.getDescription());
				}

				// for sections
				else
				{
					Map<String, Object> groupMap = new HashMap<String, Object>();
					sectionsCollection.add(groupMap);

					groupMap.put("title", group.getTitle());
					if (group.getDescription() != null) groupMap.put("description", group.getDescription());
				}
			}

			if (!groupsCollection.isEmpty()) artifact.getProperties().put("groups", groupsCollection);
			if (!sectionsCollection.isEmpty()) artifact.getProperties().put("sections", sectionsCollection);
			
			// external service
			// web content - information is in the site's tools
		
			Collection<ToolConfiguration> webContentTools = site.getTools("sakai.iframe");
			for (ToolConfiguration tool : webContentTools)
			{
				Properties toolProps = tool.getConfig();
				// check for external service
				String thirdPartyService = toolProps.getProperty("thirdPartyService");
				if (!"Yes".equalsIgnoreCase(thirdPartyService)) continue;
				
				Map<String, Object> thirdPartyMap = new HashMap<String, Object>();
				externalServiceCollection.add(thirdPartyMap);
						
				String key = toolProps.getProperty("key");
				String secret = toolProps.getProperty("secret");
				String extraInformation = toolProps.getProperty("extraInformation");				
				Boolean newPage = site.getPage(tool.getPageId()).isPopUp();
				String pageTitle = site.getPage(tool.getPageId()).getTitle();

				thirdPartyMap.put("toolTitle", tool.getTitle());
				thirdPartyMap.put("height", toolProps.getProperty("height"));
				thirdPartyMap.put("source", toolProps.getProperty("source"));
				if (key != null) thirdPartyMap.put("key", key);
				if (secret != null) thirdPartyMap.put("secret", secret);
				if (extraInformation != null) thirdPartyMap.put("extraInformation", extraInformation);
				thirdPartyMap.put("thirdPartyService", thirdPartyService);								
				thirdPartyMap.put("newPage", newPage);
				thirdPartyMap.put("pageTitle", pageTitle);			
			}		
			
			if (!externalServiceCollection.isEmpty()) artifact.getProperties().put("externalServices", externalServiceCollection);
			
		}
		catch (IdUnusedException e)
		{
		}
	
		// archive it
		archive.archive(artifact);
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
