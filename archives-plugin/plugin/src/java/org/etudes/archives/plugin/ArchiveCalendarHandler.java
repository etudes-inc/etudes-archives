/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveCalendarHandler.java $
 * $Id: ArchiveCalendarHandler.java 2922 2012-05-10 22:49:02Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2012 Etudes, Inc.
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
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.XrefHelper;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.util.StringUtil;

/**
 * Archives archive handler for Calendar
 */
public class ArchiveCalendarHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveCalendarHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.schedule";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: CalendarService. */
	protected CalendarService calendarService = null;

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// site's calendar is /calendar/calendar/<site id>/main
		String calendarRef = "/calendar/calendar/" + siteId + "/main";

		try
		{
			Calendar calendar = this.calendarService.getCalendar(calendarRef);

			// store an artifact for the calendar?
			Artifact artifact = archive.newArtifact(applicationId, calendar.getReference());
			String fields = calendar.getEventFields();
			if ((fields != null) && (!fields.equals("")))
			{
				artifact.getProperties().put("fields", calendar.getEventFields());

				// archive the calendar only if we have fields
				archive.archive(artifact);
			}

			// read all the events
			List<CalendarEvent> events = calendar.getEvents(null, null);
			for (CalendarEvent event : events)
			{
				// find embedded document references
				String body = event.getDescriptionFormatted();
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(body, null);

				// get the attachments; combine with the references and make a reference string list for archiving
				List<Reference> attachments = event.getAttachments();
				List<String> attachmentReferences = new ArrayList<String>();
				for (Reference attachment : attachments)
				{
					refs.add(attachment.getReference());
					attachmentReferences.add(attachment.getReference());
				}

				// make an artifact
				artifact = archive.newArtifact(applicationId, event.getReference());

				// set the announcement information into the properties
				artifact.getProperties().put("title", event.getDisplayName());
				artifact.getProperties().put("description", body);
				artifact.getProperties().put("type", event.getType());
				artifact.getProperties().put("location", event.getLocation());
				artifact.getProperties().put("range", event.getRange().toString());
				// event.getCreator()
				// event.getModifiedBy()
				RecurrenceRule rule = event.getRecurrenceRule();
				if (rule != null)
				{
					artifact.getProperties().put("recurrenceCount", rule.getCount());
					artifact.getProperties().put("recurrenceType", rule.getFrequencyDescription());
					artifact.getProperties().put("recurrenceInterval", rule.getInterval());
					if (rule.getUntil() != null) artifact.getProperties().put("recurrenceUntil", rule.getUntil().toString());
				}

				artifact.getProperties().put("attachments", attachmentReferences);

				// extra fields
				String fieldsAsOne = calendar.getEventFields();
				if (fieldsAsOne != null)
				{
					String[] existingFields = StringUtil.split(fieldsAsOne, "_,_");
					int index = 0;
					for (String f : existingFields)
					{
						String val = event.getField(f);
						if (val != null)
						{
							artifact.getProperties().put("field_" + index, val);
							artifact.getProperties().put("fieldName_" + index, f);
							index++;
						}
					}

					if (index > 0) artifact.getProperties().put("fieldCount", Integer.valueOf(index));
				}

				// get any groups and sections as group titles
				List<String> groupTitles = new ArrayList<String>();
				List<String> sectionTitles = new ArrayList<String>();
				Collection<Group> groups = event.getGroupObjects();
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
}
