/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportCalendarHandler.java $
 * $Id: ImportCalendarHandler.java 5915 2013-09-11 04:51:56Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2013 Etudes, Inc.
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
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEdit;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives import handler for Calendar
 */
public class ImportCalendarHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportCalendarHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.schedule";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: CalendarService. */
	protected CalendarService calendarService = null;

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
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		if (artifact.getReference().startsWith("/calendar/calendar"))
		{
			importCalendarArtifact(siteId, artifact, archive);
		}
		else
		{
			importEventArtifact(siteId, artifact, archive);
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

	/**
	 * Compare two objects for differences, either may be null
	 * 
	 * @param a
	 *        One object.
	 * @param b
	 *        The other object.
	 * @return true if the object are different, false if they are the same.
	 */
	protected boolean different(Object a, Object b)
	{
		// if both null, they are the same
		if ((a == null) && (b == null)) return false;

		// if either are null (they both are not), they are different
		if ((a == null) || (b == null)) return true;

		// now we know neither are null, so compare
		return (!a.equals(b));
	}

	/**
	 * {@inheritDoc}
	 */
	protected void importCalendarArtifact(String siteId, Artifact artifact, Archive archive)
	{
		// site's calendar is /calendar/calendar/<site id>/main
		String calendarRef = "/calendar/calendar/" + siteId + "/main";

		try
		{
			Calendar calendar = null;
			try
			{
				calendar = this.calendarService.getCalendar(calendarRef);
			}
			catch (IdUnusedException e)
			{
				try
				{
					// create the calendar
					calendar = this.calendarService.addCalendar(calendarRef);
				}
				catch (IdInvalidException e2)
				{
					M_log.warn("importCalendarArtifact: " + e.toString());
				}
				catch (IdUsedException e2)
				{
					M_log.warn("importCalendarArtifact: " + e.toString());
				}
			}

			if (calendar == null)
			{
				M_log.warn("importCalendarArtifact: cannot find or create calendar: " + calendarRef);
			}

			String fieldsAsOne = (String) artifact.getProperties().get("fields");
			if (fieldsAsOne != null)
			{
				String existingFieldsAsOne = calendar.getEventFields();
				Set<String> existingFieldsSet = new HashSet<String>();
				if (existingFieldsAsOne != null)
				{
					String[] existingFields = StringUtil.split(existingFieldsAsOne, "_,_");
					for (String f : existingFields)
					{
						existingFieldsSet.add(f);
					}
				}

				// parse the import fields by _,_
				String[] fields = StringUtil.split(fieldsAsOne, "_,_");

				// add the new fields to the end of the list
				String newFieldsAsOne = existingFieldsAsOne;
				if (newFieldsAsOne == null) newFieldsAsOne = "";
				for (String f : fields)
				{
					if (existingFieldsSet.contains(f)) continue;

					if (newFieldsAsOne.length() > 0) newFieldsAsOne += "_,_";
					newFieldsAsOne += f;

					// in case the source had duplicates, add the field to the existingFieldsSet
					existingFieldsSet.add(f);
				}

				try
				{
					CalendarEdit edit = this.calendarService.editCalendar(calendarRef);
					edit.setEventFields(newFieldsAsOne);
					this.calendarService.commitCalendar(edit);
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importCalendarArtifact: " + e.toString());
				}
				catch (InUseException e)
				{
					M_log.warn("importCalendarArtifact: " + e.toString());
				}
			}
		}

		catch (PermissionException e)
		{
			M_log.warn("importCalendarArtifact: " + e.toString());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	protected void importEventArtifact(String siteId, Artifact artifact, Archive archive)
	{
		// site's calendar is /calendar/calendar/<site id>/main
		String calendarRef = "/calendar/calendar/" + siteId + "/main";

		try
		{
			Calendar calendar = null;
			try
			{
				calendar = this.calendarService.getCalendar(calendarRef);
			}
			catch (IdUnusedException e)
			{
				try
				{
					// create the calendar
					calendar = this.calendarService.addCalendar(calendarRef);
				}
				catch (IdInvalidException e2)
				{
					M_log.warn("importEventArtifact: " + e.toString());
				}
				catch (IdUsedException e2)
				{
					M_log.warn("importEventArtifact: " + e.toString());
				}
			}

			if (calendar == null)
			{
				M_log.warn("importEventArtifact: cannot find or create calendar: " + calendarRef);
			}

			// translate embedded references in the body
			String description = (String) artifact.getProperties().get("description");
			description = XrefHelper.translateEmbeddedReferences(description, archive.getTranslations(), null, null);

			String title = (String) artifact.getProperties().get("title");

			CalendarEventEdit edit = calendar.addEvent();

			edit.setDescriptionFormatted(description);
			edit.setDisplayName(title);
			edit.setType((String) artifact.getProperties().get("type"));
			edit.setLocation((String) artifact.getProperties().get("location"));

			TimeRange range = this.timeService.newTimeRange((String) artifact.getProperties().get("range"));
			edit.setRange(range);

			String recurrenceType = (String) artifact.getProperties().get("recurrenceType");
			if (recurrenceType != null)
			{
				Integer interval = (Integer) artifact.getProperties().get("recurrenceInterval");
				Integer count = (Integer) artifact.getProperties().get("recurrenceCount");
				Time until = null;
				String val = (String) artifact.getProperties().get("recurrenceUntil");
				if (val != null) until = this.timeService.newTimeGmt(val);
				RecurrenceRule rule = null;
				if (until != null)
				{
					if (interval != null)
					{
						rule = this.calendarService.newRecurrence(recurrenceType, interval.intValue(), until);
					}
				}
				else
				{
					if (count != null)
					{
						rule = this.calendarService.newRecurrence(recurrenceType, interval.intValue(), count.intValue());
					}
				}
				edit.setRecurrenceRule(rule);
			}

			// fields
			Integer fieldCount = (Integer) artifact.getProperties().get("fieldCount");
			if (fieldCount != null)
			{
				for (int i = 0; i < fieldCount.intValue(); i++)
				{
					String val = (String) artifact.getProperties().get("field_" + i);
					String name = (String) artifact.getProperties().get("fieldName_" + i);
					edit.setField(name, val);
				}
			}

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
						edit.setGroupAccess(announcementGroups, true);
					}
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importEventArtifact: missing site: " + siteId);
				}
			}

			// before we bring in attachments, lets see if we want to reject this event as "matching" an existing event
			if (calendar.containsMatchingEvent(edit))
			{
				calendar.removeEvent(edit);
			}

			// continue with attachments and commit the changes
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
						edit.addAttachment(ref);
					}
				}

				calendar.commitEvent(edit, 0);
			}
		}
		catch (PermissionException e)
		{
			M_log.warn("importEventArtifact: " + e.toString());
		}
	}
}
