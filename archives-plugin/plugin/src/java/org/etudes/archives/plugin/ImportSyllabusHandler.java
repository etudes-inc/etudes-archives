/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportSyllabusHandler.java $
 * $Id: ImportSyllabusHandler.java 8238 2014-06-12 18:29:58Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, 2014 Etudes, Inc.
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

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.util.HtmlHelper;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.api.app.syllabus.SyllabusAttachment;
import org.sakaiproject.api.app.syllabus.SyllabusData;
import org.sakaiproject.api.app.syllabus.SyllabusItem;
import org.sakaiproject.api.app.syllabus.SyllabusManager;
import org.sakaiproject.component.app.syllabus.SyllabusDataImpl;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.user.cover.UserDirectoryService;

/**
 * Archives import handler for Syllabus
 */
public class ImportSyllabusHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportSyllabusHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.syllabus";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: EntityManager */
	protected EntityManager entityManager = null;

	/** Dependency: SyllabusManager. */
	protected SyllabusManager syllabusManager;

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
	@SuppressWarnings(
	{ "unchecked", "rawtypes" })
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		// get the main entry (item) for the site
		SyllabusItem syllabusItem = syllabusManager.getSyllabusItemByContextId(siteId);
		if (syllabusItem == null)
		{
			// create it if needed
			syllabusItem = syllabusManager.createSyllabusItem(UserDirectoryService.getCurrentUser().getId(), siteId, null /* redirect url */);
		}

		String redirect = (String) artifact.getProperties().get("redirect");
		if (redirect != null)
		{
			Boolean openWindow = (Boolean) artifact.getProperties().get("openWindow");
			if (openWindow == null) openWindow = Boolean.FALSE;

			// we archived the redirect
			syllabusItem.setRedirectURL(redirect);
			syllabusItem.setOpenNewWindow(openWindow);
			this.syllabusManager.saveSyllabusItem(syllabusItem);
		}

		else
		{
			// what was archived was a "syllabusData"
			SyllabusData syData = new SyllabusDataImpl();

			// translate embedded references in the body
			String body = (String) artifact.getProperties().get("body");
			body = HtmlHelper.clean(body);
			body = XrefHelper.translateEmbeddedReferences(body, archive.getTranslations(), null, null);

			syData.setAsset(body);
			syData.setTitle((String) artifact.getProperties().get("title"));
			syData.setStatus((String) artifact.getProperties().get("status"));
			syData.setEmailNotification("none");

			// syData.setPosition((Integer) artifact.getProperties().get("position"));
			Integer positionNo = new Integer(syllabusManager.findLargestSyllabusPosition(syllabusItem).intValue() + 1);
			syData.setPosition(positionNo);

			syData.setView((String) artifact.getProperties().get("pubview"));

			// attachments
			List<String> attachments = (List<String>) artifact.getProperties().get("attachments");
			if (attachments != null)
			{
				Set<SyllabusAttachment> attachSet = new TreeSet<SyllabusAttachment>();
				for (String attachment : attachments)
				{
					// change to the imported site's attachment
					for (Translation t : archive.getTranslations())
					{
						attachment = t.translate(attachment);
					}

					// find the attachment
					String attachmentId = attachment.substring("/content".length());
					try
					{
						ContentResource content = ContentHostingService.getResource(attachmentId);
						ResourceProperties properties = content.getProperties();

						SyllabusAttachment syllabusAttachment = syllabusManager.createSyllabusAttachmentObject(content.getId(),
								properties.getProperty(ResourceProperties.PROP_DISPLAY_NAME));
						syllabusAttachment.setSize(properties.getProperty(ResourceProperties.PROP_CONTENT_LENGTH));
						syllabusAttachment.setType(properties.getProperty(ResourceProperties.PROP_CONTENT_TYPE));
						syllabusAttachment.setUrl(content.getUrl());

						attachSet.add(syllabusAttachment);
					}
					catch (IdUnusedException e)
					{
						M_log.warn("importArtifact: " + e.toString());
					}
					catch (TypeException e)
					{
						M_log.warn("importArtifact: " + e.toString());
					}
					catch (PermissionException e)
					{
						M_log.warn("importArtifact: " + e.toString());
					}
				}

				syData.setAttachments(attachSet);
			}

			// if the title and body match something already here, skip it
			boolean addIt = true;
			Set fromSyDataSet = syllabusManager.getSyllabiForSyllabusItem(syllabusItem);
			if (fromSyDataSet != null && fromSyDataSet.size() > 0)
			{
				Iterator fromSetIter = fromSyDataSet.iterator();
				while (fromSetIter.hasNext())
				{
					SyllabusData sData = (SyllabusData) fromSetIter.next();
					// Note: trim, because if an existing entry has trailing white space, older archives (<2014) will have had that trimmed -ggolden
					if (!different(sData.getAsset().trim(), syData.getAsset().trim()) && !different(sData.getTitle(), syData.getTitle()))
					{
						addIt = false;
						break;
					}
				}
			}

			// add it to the syllabus
			if (addIt)
			{
				this.syllabusManager.addSyllabusToSyllabusItem(syllabusItem, syData);
			}
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
	 * Set the SyllabusManager.
	 * 
	 * @param service
	 *        The SyllabusManager.
	 */
	public void setSyllabusManager(SyllabusManager service)
	{
		this.syllabusManager = service;
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
}
