/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportHomepageHandler.java $
 * $Id: ImportHomepageHandler.java 8249 2014-06-12 23:42:17Z ggolden $
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

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.homepage.api.HomeContent;
import org.etudes.homepage.api.HomeContentItem;
import org.etudes.homepage.api.HomePageOptions;
import org.etudes.homepage.api.HomePageService;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResource;
import org.etudes.siteresources.api.SiteResourcesService;
import org.etudes.util.Different;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.api.SessionManager;

/**
 * Archives import handler for Homepage
 */
public class ImportHomepageHandler implements ImportHandler
{
	/** The application Id. */
	protected final static String applicationId = "e3.homepage";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportHomepageHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterImportHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		if (artifact.getReference().equals("/options"))
		{
			importOptions(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/item"))
		{
			importItem(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/placement"))
		{
			importPlacement(siteId, artifact, archive);
		}
		else
		{
			M_log.warn("importArtifact: unknown type: " + artifact.getReference());
		}
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerImportHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerFilteredReferences(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		// if importing, add the references
		archive.getReferences().addAll(artifact.getReferences());
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
	 * Import a homepage item.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	protected void importItem(String siteId, Artifact artifact, Archive archive)
	{
		String altText = (String) artifact.getProperties().get("altText");
		Date releaseDate = null;
		if (artifact.getProperties().get("releaseDate") != null)
		{
			releaseDate = new Date((Long) artifact.getProperties().get("releaseDate"));
		}

		// Boolean published = (Boolean) artifact.getProperties().get("published");
		String source = (String) artifact.getProperties().get("source");
		String style = (String) artifact.getProperties().get("style");
		String title = (String) artifact.getProperties().get("title");
		String type = (String) artifact.getProperties().get("type");
		String url = (String) artifact.getProperties().get("url");

		try
		{
			HomeContentItem item = homePageService().newContentItem(sessionManager().getCurrentSessionUserId(), siteId);

			item.setAltText(altText);

			// start with items unpublished
			item.setPublished(Boolean.FALSE);

			item.setReleaseDate(releaseDate);
			item.setSource(source);
			item.setStyle(style);
			item.setTitle(title);
			item.setType(type);

			if ("A".equals(source))
			{
				Integer length = (Integer) artifact.getProperties().get("length");
				byte[] body = archive.readFile((String) artifact.getProperties().get("body"), length.intValue());
				String content = new String(body, "UTF-8");

				item.setNewContent(content);
			}
			else
			{
				item.setUrl(url);
			}

			// let the service weed out dups
			homePageService().saveContentItemIfNotDuplicate(sessionManager().getCurrentSessionUserId(), item);
		}
		catch (PermissionException e)
		{
			M_log.warn("importItem: " + e);
		}
		catch (UnsupportedEncodingException e)
		{
			M_log.warn("importItem: " + e);
		}
	}

	/**
	 * Import homepage options.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	protected void importOptions(String siteId, Artifact artifact, Archive archive)
	{
		try
		{
			// don't replace a site's existing options
			if (homePageService().hasOptions(sessionManager().getCurrentSessionUserId(), siteId)) return;

			HomePageOptions options = homePageService().getOptions(sessionManager().getCurrentSessionUserId(), siteId);
			Boolean announcementsEnabled = (Boolean) artifact.getProperties().get("announcementsEnabled");
			Boolean chatEnabled = (Boolean) artifact.getProperties().get("chatEnabled");
			String format = (String) artifact.getProperties().get("format");
			Integer numAnnouncements = (Integer) artifact.getProperties().get("numAnnouncements");
			String order = (String) artifact.getProperties().get("order");
			Boolean scheduleEnabled = (Boolean) artifact.getProperties().get("scheduleEnabled");

			options.setAnnouncementsEnabled(announcementsEnabled);
			options.setChatEnabled(chatEnabled);
			options.setFormat(format);
			options.setNumAnnouncements(numAnnouncements);
			options.setOrder(order);
			options.setScheduleEnabled(scheduleEnabled);

			homePageService().saveOptions(sessionManager().getCurrentSessionUserId(), options);
		}
		catch (PermissionException e)
		{
			M_log.warn("importOptions: " + e);
		}
	}

	/**
	 * Import homepage site resource placement.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	protected void importPlacement(String siteId, Artifact artifact, Archive archive)
	{
		String name = (String) artifact.getProperties().get("name");
		Long resourceId = (Long) artifact.getProperties().get("id");
		Integer length = (Integer) artifact.getProperties().get("length");
		String mimeType = (String) artifact.getProperties().get("mimeType");

		// the site might already have a placement with this name
		SitePlacement placement = siteResourcesService().getSitePlacement(siteId, name);

		// if there's already a site resource in the site with this name, we are just going to use it.
		if (placement == null)
		{
			// the resource might still be around
			SiteResource resource = siteResourcesService().getSiteResource(resourceId);

			// if we don't find the original site resource, recreate it (as a new site resource)
			if (resource == null)
			{
				byte[] body = archive.readFile((String) artifact.getProperties().get("body"), length.intValue());
				ByteArrayInputStream contents = new ByteArrayInputStream(body);
				siteResourcesService().addSiteResource(mimeType, length.intValue(), contents, siteId, name);
			}

			// if it does exist, add a new placement for this site
			else
			{
				siteResourcesService().addSitePlacement(resource, siteId, name);
			}
		}
	}

	/**
	 * @return The SessionManager, via the component manager.
	 */
	private SessionManager sessionManager()
	{
		return (SessionManager) ComponentManager.get(SessionManager.class);
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
