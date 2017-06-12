/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefCalendarHandler.java $
 * $Id: XrefCalendarHandler.java 2903 2012-05-03 23:46:24Z ggolden $
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

import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives cross reference handler for Calendar
 */
public class XrefCalendarHandler implements XrefHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefCalendarHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.schedule";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: CaneldarService. */
	protected CalendarService calendarService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

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
	@SuppressWarnings("unchecked")
	public int removeXref(String siteId)
	{
		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		// site's calendar is /calendar/calendar/<site id>/main
		String ref = "/calendar/calendar/" + siteId + "/main";

		int count = 0;

		try
		{
			// read all the events for the site
			Calendar calendar = this.calendarService.getCalendar(ref);
			List<CalendarEvent> events = calendar.getEvents(null, null);

			for (CalendarEvent event : events)
			{
				String text = event.getDescriptionFormatted();

				// find the embedded cross-site document references
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(text, siteId);
				count += refs.size();

				// copy them somewhere in the site
				List<Translation> translations = XrefHelper.importTranslateResources(refs, siteId, "Schedule");

				// update text with the new locations; also shorten any full URLs to this server
				String newText = XrefHelper.translateEmbeddedReferencesAndShorten(text, translations, siteId, null);

				// attachments
				List<Reference> attachments = (List<Reference>) event.getAttachments();
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

				// update the event if changed
				if (StringUtil.different(text, newText))
				{
					try
					{
						CalendarEventEdit edit = calendar.getEditEvent(event.getId(), "");
						edit.setDescriptionFormatted(newText);
						calendar.commitEvent(edit, 0);
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
	 * Set the calendar service.
	 * 
	 * @param service
	 *        The calendar service.
	 */
	public void setCalendarService(CalendarService service)
	{
		this.calendarService = service;
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
}
