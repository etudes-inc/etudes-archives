/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveAnnouncementsHandler.java $
 * $Id: ArchiveAnnouncementsHandler.java 12215 2015-12-04 19:08:50Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, 2015 Etudes, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.XrefHelper;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.time.api.Time;

/**
 * Archives archive handler for Announcements
 */
public class ArchiveAnnouncementsHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveAnnouncementsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.announcements";

	/** Dependency: AnnouncementService. */
	protected AnnouncementService announcementService = null;

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// site's channel is /announcement/channel/<site id>/main
		String channelRef = "/announcement/channel/" + siteId + "/main";

		try
		{
			AnnouncementChannel channel = this.announcementService.getAnnouncementChannel(channelRef);

			// store an artifact for the channel?
			// if not, assume "main" with no properties

			// read all the announcements for the site
			List<AnnouncementMessage> messages = channel.getAllMessages(null, true);
			for (AnnouncementMessage message : messages)
			{
				// find embedded document references
				String body = message.getBody();
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(body, null);

				// get the attachments; combine with the references and make a reference string list for archiving
				List<Reference> attachments = message.getAnnouncementHeader().getAttachments();
				List<String> attachmentReferences = new ArrayList<String>();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
					attachmentReferences.add(attachment.getReference());
				}

				// make an artifact
				Artifact artifact = archive.newArtifact(applicationId, message.getReference());

				// set the announcement information into the properties
				artifact.getProperties().put("subject", message.getAnnouncementHeader().getSubject());
				artifact.getProperties().put("body", body);
				artifact.getProperties().put("access", message.getAnnouncementHeader().getAccess().toString());
				artifact.getProperties().put("draft", Boolean.toString(message.getAnnouncementHeader().getDraft()));
				artifact.getProperties().put("attachments", attachmentReferences);
				if (message.getProperties().getProperty("archived") != null)
				{
					artifact.getProperties().put("archived", message.getProperties().getProperty("archived"));
				}
				try
				{
					Time archivedDate = message.getProperties().getTimeProperty("archivedDate");
					if (archivedDate != null)
					{
						artifact.getProperties().put("archivedDate", Long.valueOf(archivedDate.getTime()));
					}
				}
				catch (EntityPropertyNotDefinedException e)
				{
				}
				catch (EntityPropertyTypeException e)
				{
				}
				if (message.getAnnouncementHeader().getDate() != null)
					artifact.getProperties().put("date", Long.valueOf(message.getAnnouncementHeader().getDate().getTime()));
				if (message.getAnnouncementHeader().getFrom() != null)
				{
					artifact.getProperties().put("fromId", message.getAnnouncementHeader().getFrom().getId());
					artifact.getProperties().put("from", message.getAnnouncementHeader().getFrom().getDisplayName());
				}
				try
				{
					artifact.getProperties().put("public",
							Boolean.valueOf(message.getProperties().getBooleanProperty(ResourceProperties.PROP_PUBVIEW)));
				}
				catch (EntityPropertyNotDefinedException e)
				{
				}
				catch (EntityPropertyTypeException e)
				{
				}

				String position = message.getProperties().getProperty("position");
				if (position != null) artifact.getProperties().put("position", position);

				// get any groups and sections as group titles
				List<String> groupTitles = new ArrayList<String>();
				List<String> sectionTitles = new ArrayList<String>();
				Collection<Group> groups = message.getAnnouncementHeader().getGroupObjects();
				for (Group group : groups)
				{
					// for groups
					if (group.getProperties().getProperty("sections_category") == null)
					{
						groupTitles.add(group.getTitle());
					}

					// for sections
					else
					{
						sectionTitles.add(group.getTitle());
					}
				}
				if (!groupTitles.isEmpty()) artifact.getProperties().put("groups", groupTitles);
				if (!sectionTitles.isEmpty()) artifact.getProperties().put("sections", sectionTitles);

				// release date
				try
				{
					Time releaseDate = message.getProperties().getTimeProperty(AnnouncementService.RELEASE_DATE);
					if (releaseDate != null)
					{
						artifact.getProperties().put("releaseDate", Long.valueOf(releaseDate.getTime()));
					}
				}
				catch (EntityPropertyNotDefinedException e)
				{
				}
				catch (EntityPropertyTypeException e)
				{
				}

				// record the references
				artifact.getReferences().addAll(refs);

				// archive it
				archive.archive(artifact);
			}
		}
		catch (IdUnusedException e)
		{
			// M_log.warn("removeXref: " + e);
		}
		catch (PermissionException e)
		{
			M_log.warn("archive: " + e);
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
	 * Set the announcement service.
	 * 
	 * @param service
	 *        The announcement service.
	 */
	public void setAnnouncementService(AnnouncementService service)
	{
		this.announcementService = service;
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
}
