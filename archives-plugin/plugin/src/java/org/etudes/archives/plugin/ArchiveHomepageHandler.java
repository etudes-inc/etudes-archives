/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveHomepageHandler.java $
 * $Id: ArchiveHomepageHandler.java 8245 2014-06-12 22:44:18Z ggolden $
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
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.homepage.api.HomeContent;
import org.etudes.homepage.api.HomeContentItem;
import org.etudes.homepage.api.HomePageOptions;
import org.etudes.homepage.api.HomePageService;
import org.etudes.siteresources.api.SitePlacement;
import org.etudes.siteresources.api.SiteResource;
import org.etudes.siteresources.api.SiteResourcesService;
import org.etudes.siteresources.api.ToolReference;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

/**
 * Archives archive handler for Homepage
 */
public class ArchiveHomepageHandler implements ArchiveHandler
{
	/** The application Id. */
	protected final static String applicationId = "e3.homepage";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveHomepageHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// all site resources that have a placement in the site, except those "." ones used as home page authored content body storage
		List<SitePlacement> placements = siteResourcesService().getSitePlacements(siteId);
		for (SitePlacement placement : placements)
		{
			if (placement.getName().startsWith(".")) continue;

			SiteResource resource = placement.getSiteResource();

			Artifact artifact = archive.newArtifact(applicationId, "/placement/" + placement.getName());

			artifact.getProperties().put("name", placement.getName());
			artifact.getProperties().put("id", resource.getId());
			if (resource.getDate() != null) artifact.getProperties().put("date", resource.getDate().getTime());
			artifact.getProperties().put("length", Integer.valueOf(resource.getLength()));
			artifact.getProperties().put("mimeType", resource.getMimeType());

			// the body
			artifact.getProperties().put("body", resource.getStream());

			// archive it
			archive.archive(artifact);
		}

		// options - if defined
		try
		{
			if (homePageService().hasOptions("admin", siteId))
			{
				HomePageOptions options = homePageService().getOptions("admin", siteId);

				Artifact artifact = archive.newArtifact(applicationId, "/options");

				artifact.getProperties().put("announcementsEnabled", options.getAnnouncementsEnabled());
				artifact.getProperties().put("chatEnabled", options.getChatEnabled());
				artifact.getProperties().put("format", options.getFormat());

				if (options.getModifiedDate() != null) artifact.getProperties().put("modifiedOn", options.getModifiedDate().getTime());
				if (options.getModifiedUser() != null)
				{
					try
					{
						artifact.getProperties().put("modifiedBy", this.userDirectoryService.getUser(options.getModifiedUser()).getDisplayName());
					}
					catch (UserNotDefinedException e)
					{
					}
				}
				artifact.getProperties().put("numAnnouncements", options.getNumAnnouncements());
				artifact.getProperties().put("order", options.getOrder());
				artifact.getProperties().put("scheduleEnabled", options.getScheduleEnabled());

				// archive it
				archive.archive(artifact);
			}
		}
		catch (PermissionException e)
		{
			M_log.warn("archive: " + e);
		}

		// items
		HomeContent content = homePageService().getContent(siteId);
		for (HomeContentItem item : content.getItems())
		{
			Artifact artifact = archive.newArtifact(applicationId, "/item/" + item.getId());

			artifact.getProperties().put("id", item.getId());
			artifact.getProperties().put("altText", item.getAltText());

			if (item.getModifiedUser() != null)
			{
				try
				{
					artifact.getProperties().put("modifiedBy", this.userDirectoryService.getUser(item.getModifiedUser()).getDisplayName());
				}
				catch (UserNotDefinedException e)
				{
				}
			}
			if (item.getModifiedDate() != null) artifact.getProperties().put("modifiedOn", item.getModifiedDate().getTime());
			artifact.getProperties().put("published", item.getPublished());
			if (item.getReleaseDate() != null) artifact.getProperties().put("releaseDate", item.getReleaseDate().getTime());
			artifact.getProperties().put("source", item.getSource());
			artifact.getProperties().put("style", item.getStyle());
			artifact.getProperties().put("title", item.getTitle());
			artifact.getProperties().put("type", item.getType());
			artifact.getProperties().put("url", item.getUrl());

			if ("A".equals(item.getSource()))
			{
				// record our site resource reference
				ToolReference ref = siteResourcesService().parseToolReferenceUrl(item.getUrl());
				if ((ref != null) && (ref.getId() != null))
				{
					SitePlacement placement = siteResourcesService().getSitePlacement(ref.getSiteId(), ref.getName());
					if (placement != null)
					{
						SiteResource resource = placement.getSiteResource();

						artifact.getProperties().put("length", Integer.valueOf(resource.getLength()));
						artifact.getProperties().put("mimeType", resource.getMimeType());
						artifact.getProperties().put("body", resource.getStream());
					}
					else
					{
						// this should not happen
						M_log.warn("archive - null placement from url for A: " + item.getUrl());
					}
				}
				else
				{
					// this should not happen
					M_log.warn("archive - null or null id ref from url for A/F: " + item.getUrl());
				}
			}

			// archive it
			archive.archive(artifact);
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
	 * Set the UserDirectoryService.
	 * 
	 * @param service
	 *        The UserDirectoryService.
	 */
	public void setUserDirectoryService(UserDirectoryService service)
	{
		this.userDirectoryService = service;
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
