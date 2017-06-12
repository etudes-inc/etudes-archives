/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefAnnouncementsHandler.java $
 * $Id: XrefAnnouncementsHandler.java 2823 2012-04-03 20:57:39Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009 Etudes, Inc.
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
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementMessageEdit;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives cross reference handler for Announcements
 */
public class XrefAnnouncementsHandler implements XrefHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefAnnouncementsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.announcements";

	/** Dependency: AnnouncementService. */
	protected AnnouncementService announcementService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterXrefHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerXrefHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public int removeXref(String siteId)
	{
		// ignore user sites
		if (siteId.startsWith("~")) return 0;

		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		// site's channel is /announcement/channel/<site id>/main
		String ref = "/announcement/channel/" + siteId + "/main";

		int count = 0;

		try
		{
			// read all the announcements for the site
			AnnouncementChannel channel = this.announcementService.getAnnouncementChannel(ref);
			List<AnnouncementMessage> messages = channel.getMessages(null, true);

			for (AnnouncementMessage message : messages)
			{
				String text = message.getBody();

				// find the embedded cross-site document references
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(text, siteId);
				count += refs.size();

				// copy them somewhere in the site
				List<Translation> translations = XrefHelper.importTranslateResources(refs, siteId, "Announcements");

				// update text with the new locations; also shorten any full URLs to this server
				String newText = XrefHelper.translateEmbeddedReferencesAndShorten(text, translations, siteId, null);

				// attachments
				List<Reference> attachments = (List<Reference>) message.getHeader().getAttachments();
				for (Reference attachmentRef : attachments)
				{
					try
					{
						// get the attachment
						ContentResource attachment = this.contentHostingService.getResource(attachmentRef.getId());

						// harvest any references to the site, translating as needed
						XrefHelper.harvestTranslateResource(attachment, siteId, "Announcements");
					}
					catch (TypeException e)
					{
						M_log.warn("removeXref: " + e);
					}
					catch (IdUnusedException e)
					{
					}
					catch (PermissionException e)
					{
						M_log.warn("removeXref: " + e);
					}
				}

				// update the message if changed
				if (StringUtil.different(text, newText))
				{
					try
					{
						AnnouncementMessageEdit edit = channel.editAnnouncementMessage(message.getId());
						edit.setBody(newText);
						channel.commitMessage(edit, 0);
					}
					catch (IdUnusedException e)
					{
						M_log.warn("removeXref: " + e);
					}
					catch (PermissionException e)
					{
						M_log.warn("removeXref: " + e);
					}
					catch (InUseException e)
					{
						M_log.warn("removeXref: " + e);
					}
				}
			}
		}
		catch (IdUnusedException e)
		{
			// M_log.warn("removeXref: " + e);
		}
		catch (PermissionException e)
		{
			M_log.warn("removeXref: " + e);
		}

		return count;
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
	 * Set the ContentHostingService.
	 * 
	 * @param service
	 *        The ContentHostingService.
	 */
	public void setContentHostingService(ContentHostingService service)
	{
		this.contentHostingService = service;
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
