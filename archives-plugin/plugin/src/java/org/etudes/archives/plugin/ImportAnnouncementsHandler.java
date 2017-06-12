/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportAnnouncementsHandler.java $
 * $Id: ImportAnnouncementsHandler.java 12215 2015-12-04 19:08:50Z mallikamt $
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessageEdit;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.TimeService;

/**
 * Archives import handler for Announcements
 */
public class ImportAnnouncementsHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportAnnouncementsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.announcements";

	/** Dependency: AnnouncementService. */
	protected AnnouncementService announcementService = null;

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: EntityManager */
	protected EntityManager entityManager = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: TimeService */
	protected TimeService timeService = null;

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
	@SuppressWarnings("unchecked")
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		// site's channel is /announcement/channel/<site id>/main
		String channelRef = "/announcement/channel/" + siteId + "/main";

		try
		{
			AnnouncementChannel channel = null;
			try
			{
				channel = this.announcementService.getAnnouncementChannel(channelRef);
			}
			catch (IdUnusedException e)
			{
				try
				{
					// create the channel
					channel = this.announcementService.addAnnouncementChannel(channelRef);
				}
				catch (IdInvalidException e2)
				{
					M_log.warn("importArtifact: " + e.toString());
				}
				catch (IdUsedException e2)
				{
					M_log.warn("importArtifact: " + e.toString());
				}
			}

			if (channel == null)
			{
				M_log.warn("importArtifact: cannot find or create channel: " + channelRef);
			}

			// translate embedded references in the body
			String body = (String) artifact.getProperties().get("body");
			body = XrefHelper.translateEmbeddedReferences(body, archive.getTranslations(), null, null);

			String subject = (String) artifact.getProperties().get("subject");

			AnnouncementMessageEdit edit = channel.addAnnouncementMessage();

			edit.setBody(body);
			edit.getAnnouncementHeaderEdit().setSubject(subject);

			Boolean pubView = (Boolean) artifact.getProperties().get("public");
			if (pubView != null)
			{
				edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_PUBVIEW, pubView.toString());
			}

			String position = (String) artifact.getProperties().get("position");
			if (position != null) edit.getPropertiesEdit().addProperty("position", position);

			List<String> groupTitles = (List<String>) artifact.getProperties().get("groups");
			List<String> sectionTitles = (List<String>) artifact.getProperties().get("sections");
			List<String> combined = new ArrayList<String>();
			if (groupTitles != null) combined.addAll(groupTitles);
			if (sectionTitles != null) combined.addAll(sectionTitles);
			if (!combined.isEmpty())
			{
				try
				{
					Site s = this.siteService.getSite(siteId);
					Collection<Group> groups = (Collection<Group>) s.getGroups();

					Set<Group> announcementGroups = new HashSet<Group>();
					for (String groupTitle : combined)
					{
						for (Group g : groups)
						{
							if (g.getTitle().equals(groupTitle))
							{
								announcementGroups.add(g);
								break;
							}
						}
					}

					if (!announcementGroups.isEmpty())
					{
						edit.getAnnouncementHeaderEdit().setGroupAccess(announcementGroups);
					}
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importArtifact: missing site: " + siteId);
				}
			}

			// set all imported announcements to draft
			edit.getAnnouncementHeaderEdit().setDraft(true);

			if (artifact.getProperties().get("archived") != null)
			{
				edit.getPropertiesEdit().addProperty("archived", (String)artifact.getProperties().get("archived"));
			}
			if (artifact.getProperties().get("archivedDate") != null)
			{
				edit.getPropertiesEdit().addProperty("archivedDate",
						this.timeService.newTime((Long) artifact.getProperties().get("archivedDate")).toString());
			}
			// Note: user and date are set automatically

			if (artifact.getProperties().get("date") != null)
			{
				edit.getAnnouncementHeaderEdit().setDate(this.timeService.newTime((Long) artifact.getProperties().get("date")));
			}
			// release date
			if (artifact.getProperties().get("releaseDate") != null)
			{
				edit.getPropertiesEdit().addProperty(AnnouncementService.RELEASE_DATE,
						this.timeService.newTime((Long) artifact.getProperties().get("releaseDate")).toString());
			}

			// before we bring in attachments, lets see if we want to reject this event as "matching" an existing message
			if (channel.containsMatchingMessage(edit))
			{
				channel.cancelMessage(edit);
			}

			else
			{
				List<String> attachments = (List<String>) artifact.getProperties().get("attachments");
				if (attachments != null)
				{
					for (String attachment : attachments)
					{
						// change to the imported site's attachment
						for (Translation t : archive.getTranslations())
						{
							attachment = t.translate(attachment);
						}
						Reference ref = this.entityManager.newReference(attachment);
						edit.getAnnouncementHeaderEdit().addAttachment(ref);
					}
				}

				channel.commitMessage(edit, 0);
			}
		}
		catch (PermissionException e)
		{
			M_log.warn("importArtifact: " + e.toString());
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
	 * Set the AnnouncementService.
	 * 
	 * @param service
	 *        The AnnouncementService.
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

	/**
	 * Dependency: EntityManager.
	 * 
	 * @param service
	 *        The EntityManager.
	 */
	public void setEntityManager(EntityManager service)
	{
		this.entityManager = service;
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

	/**
	 * Dependency: TimeService.
	 * 
	 * @param service
	 *        The TimeService.
	 */
	public void setTimeService(TimeService service)
	{
		this.timeService = service;
	}
}
