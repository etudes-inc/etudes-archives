/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportMeleteHandler.java $
 * $Id: ImportMeleteHandler.java 12425 2016-01-03 23:57:29Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2016 Etudes, Inc.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.api.app.melete.MeleteResourceService;
import org.etudes.api.app.melete.ModuleDateBeanService;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.ModuleService;
import org.etudes.api.app.melete.ModuleShdatesService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.api.app.melete.SectionService;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.component.app.melete.MeleteResource;
import org.etudes.component.app.melete.Module;
import org.etudes.component.app.melete.ModuleShdates;
import org.etudes.component.app.melete.Section;
import org.etudes.util.TranslationImpl;
import org.etudes.util.api.Translation;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.SessionManager;

/**
 * Archives import handler for Melete
 */
public class ImportMeleteHandler implements ImportHandler
{
	protected class Preferences
	{
		Boolean autoNumbering;
		Boolean studentPrinting;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportMeleteHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.melete";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: ModuleService */
	protected ModuleService moduleService = null;

	/** Dependency: SectionService */
	protected SectionService sectionService = null;

	/** Dependency: SecurityService */
	protected SecurityService securityService = null;

	/** Dependency: SessionManager */
	protected SessionManager sessionManager = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** Dependency: TimeService. */
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

		if (artifact.getReference().startsWith("/module"))
		{
			addModule(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/docs"))
		{
			// nothing to process here - the references to CHS include the melete docs in the archive
			// ok, I lied. We need to make sure the site's uploads folder has the alternate reference
			fixUploadColletion(siteId);
		}
		else if (artifact.getReference().startsWith("/prefs"))
		{
			addPrefs(siteId, artifact);
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
	 * Set the ModuleService.
	 * 
	 * @param service
	 *        The ModuleService.
	 */
	public void setModuleService(ModuleService service)
	{
		this.moduleService = service;
	}

	/**
	 * Set the SectionService.
	 * 
	 * @param service
	 *        The SectionService.
	 */
	public void setSectionService(SectionService service)
	{
		this.sectionService = service;
	}

	/**
	 * Dependency: SecurityService.
	 * 
	 * @param service
	 *        The SecurityService.
	 */
	public void setSecurityService(SecurityService service)
	{
		this.securityService = service;
	}

	/**
	 * Dependency: SessionManager.
	 * 
	 * @param service
	 *        The SessionManager.
	 */
	public void setSessionManager(SessionManager service)
	{
		this.sessionManager = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Set the TimeService.
	 * 
	 * @param service
	 *        The TimeService.
	 */
	public void setTimeService(TimeService service)
	{
		this.timeService = service;
	}

	/**
	 * Add the module based on the artifact properties
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	@SuppressWarnings("unchecked")
	protected void addModule(String siteId, Artifact artifact, Archive archive)
	{
		String userId = this.sessionManager.getCurrentSessionUserId();

		// don't add if we already have something close enough
		if (moduleAlreadyExists(artifact, userId, siteId)) return;

		ModuleObjService module = new Module();
		String description = (String) artifact.getProperties().get("description");
		if (description == null) description = "";
		module.setDescription(description);
		module.setKeywords((String) artifact.getProperties().get("keywords"));
		module.setTitle((String) artifact.getProperties().get("title"));
		module.setWhatsNext((String) artifact.getProperties().get("next"));
		module.setModifyUserId(userId);

		ModuleShdatesService dates = new ModuleShdates();
		Long time = (Long) artifact.getProperties().get("end");
		Date date = null;
		if (time != null) date = new Date(time);
		dates.setEndDate(date);
		time = (Long) artifact.getProperties().get("start");
		date = null;
		if (time != null) date = new Date(time);
		dates.setStartDate(date);
		time = (Long) artifact.getProperties().get("allowUntil");
		date = null;
		if (time != null) date = new Date(time);
		dates.setAllowUntilDate(date);
		Boolean hideUntilOpen = (Boolean) artifact.getProperties().get("hideUntilOpen");
		if (hideUntilOpen != null && hideUntilOpen.booleanValue()) dates.setHideUntilStart(true);
		dates.setModule(module);

		try
		{
			this.moduleService.insertProperties(module, dates, userId, siteId);
		}
		catch (Exception e)
		{
			M_log.warn("addModule: " + e);
		}

		// sections
		Set<Translation> sectionTranslations = new HashSet<Translation>();
		Collection<Map<String, Object>> sectionColleciton = (Collection<Map<String, Object>>) artifact.getProperties().get("sections");
		if (sectionColleciton != null)
		{
			for (Map<String, Object> sectionMap : sectionColleciton)
			{
				SectionObjService section = new Section();
				section.setAudioContent((Boolean) sectionMap.get("audio"));
				section.setContentType((String) sectionMap.get("type"));
				section.setModifyUserId(userId);
				section.setOpenWindow((Boolean) sectionMap.get("window"));
				section.setTextualContent((Boolean) sectionMap.get("textual"));
				section.setTitle((String) sectionMap.get("title"));
				section.setVideoContent((Boolean) sectionMap.get("video"));

				String instructions = (String) sectionMap.get("instructions");
				if (instructions == null) instructions = "";
				section.setInstr(instructions);

				section.setModule(module);

				try
				{
					sectionService.insertSection(module, section, userId);
				}
				catch (Exception e)
				{
					M_log.warn("addModule: " + e);
				}

				// process the section content, if it exists
				Map<String, Object> resourceMap = (Map<String, Object>) sectionMap.get("resource");
				if (resourceMap.get("ref") != null)
				{
					MeleteResourceService resource = new MeleteResource();
					resource.setAllowCmrcl((Boolean) resourceMap.get("commercial"));
					resource.setAllowMod((Integer) resourceMap.get("allowModification"));
					resource.setCcLicenseUrl((String) resourceMap.get("ccLicense"));
					resource.setCopyrightOwner((String) resourceMap.get("copyrightOwner"));
					resource.setCopyrightYear((String) resourceMap.get("copyrightYear"));
					resource.setLicenseCode((Integer) resourceMap.get("license"));
					resource.setReqAttr((Boolean) resourceMap.get("reqAttribution"));

					// translate the ref, and convert ref to id
					String ref = (String) resourceMap.get("ref");
					for (Translation t : archive.getTranslations())
					{
						ref = t.translate(ref);
					}
					ref = ref.substring(ref.indexOf("/content") + "/content".length());

					// if this is a Melete-named html resource, we need to rename it with the new section id
					// /private/meleteDocs/49032b3f-35b8-4465-00bf-14b84d496637/module_2/Section_32774.html
					String oldNameSuffix = "module_" + ((Long) artifact.getProperties().get("id")).toString() + "/Section_"
							+ ((Long) sectionMap.get("id")).toString() + ".html";
					if (ref.endsWith(oldNameSuffix))
					{
						// change from the old module and section numbers to the new
						String oldModuleCollection = ref.substring(0, ref.indexOf(oldNameSuffix)) + "module_"
								+ ((Long) artifact.getProperties().get("id")).toString() + "/";
						String newCollectionName = "module_" + module.getModuleId().toString();
						String newModuleCollection = ref.substring(0, ref.indexOf(oldNameSuffix)) + newCollectionName + "/";
						String newName = "Section_" + section.getSectionId().toString() + ".html";
						String newId = newModuleCollection + newName;
						String oldId = ref;

						// rename the imported resource in content hosting
						renameChs(oldModuleCollection, newModuleCollection, newCollectionName, oldId, newId, newName);

						// update the translation with the new name
						String origRef = (String) resourceMap.get("ref");
						for (Translation translation : archive.getTranslations())
						{
							if (translation.getFrom().equals(origRef))
							{
								String newRef = "/meleteDocs/content" + newId;
								translation.setTo(newRef);
							}
						}

						ref = newId;
					}

					resource.setResourceId(ref);

					// if we don't have this in the resource table, add it and register the section's use of it
					if (!resourceExists(ref))
					{
						try
						{
							this.sectionService.insertMeleteResource(section, resource);
						}
						catch (Exception e)
						{
							M_log.warn("addModule: " + e);
						}
					}

					// if we already have this one in the resource table, just register this section's use of it
					else
					{
						try
						{
							this.sectionService.insertSectionResource(section, resource);
						}
						catch (Exception e)
						{
							M_log.warn("addModule: " + e);
						}
					}
				}

				// make sure we have entries for the resources
				List<Map<String, Object>> embeddedResources = (List<Map<String, Object>>) sectionMap.get("resources");
				if (embeddedResources != null)
				{
					for (Map<String, Object> embeddedMap : embeddedResources)
					{
						MeleteResourceService embeddedResource = new MeleteResource();
						embeddedResource.setAllowCmrcl((Boolean) embeddedMap.get("commercial"));
						embeddedResource.setAllowMod((Integer) embeddedMap.get("allowModification"));
						embeddedResource.setCcLicenseUrl((String) embeddedMap.get("ccLicense"));
						embeddedResource.setCopyrightOwner((String) embeddedMap.get("copyrightOwner"));
						embeddedResource.setCopyrightYear((String) embeddedMap.get("copyrightYear"));
						embeddedResource.setLicenseCode((Integer) embeddedMap.get("license"));
						embeddedResource.setReqAttr((Boolean) embeddedMap.get("reqAttribution"));

						// translate the ref, and convert ref to id
						String embeddedRef = (String) embeddedMap.get("ref");
						for (Translation t : archive.getTranslations())
						{
							embeddedRef = t.translate(embeddedRef);
						}
						embeddedRef = embeddedRef.substring(embeddedRef.indexOf("/content") + "/content".length());
						embeddedResource.setResourceId(embeddedRef);

						// if we don't have this in the resource table, add it and register the section's use of it
						if (!resourceExists(embeddedRef))
						{
							try
							{
								this.sectionService.insertResource(embeddedResource);
							}
							catch (Exception e)
							{
								M_log.warn("addModule: " + e);
							}
						}
					}
				}

				// record old and new section ids for updating the seqXml
				String to = "section id=\"" + section.getSectionId().toString() + "\"";
				String from = "section id=\"" + ((Long) sectionMap.get("id")).toString() + "\"";
				sectionTranslations.add(new TranslationImpl(from, to));
			}
		}

		updateModuleSeqXml(module.getModuleId(), sectionTranslations, (String) artifact.getProperties().get("seqXml"));

		Boolean archived = (Boolean) artifact.getProperties().get("archived");
		if ((archived != null) && archived.booleanValue())
		{
			updateModuleArchived(module.getModuleId());
		}
	}

	/**
	 * Add the site preferences for the module
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 */
	protected void addPrefs(String siteId, Artifact artifact)
	{
		Preferences prefs = new Preferences();
		prefs.studentPrinting = (Boolean) artifact.getProperties().get("studentPrinting");
		prefs.autoNumbering = (Boolean) artifact.getProperties().get("autoNumbering");

		addSitePrefs(prefs, siteId);
	}

	/**
	 * Add a site preferences record.
	 * 
	 * @param prefs
	 *        The preferences.
	 * @param siteId
	 *        The site id.
	 */
	protected void addSitePrefs(Preferences prefs, String siteId)
	{
		Object[] fields = new Object[3];
		fields[0] = siteId;
		fields[1] = prefs.studentPrinting ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[2] = prefs.autoNumbering ? Integer.valueOf(1) : Integer.valueOf(0);
		write("INSERT INTO MELETE_SITE_PREFERENCE (PREF_SITE_ID, PRINTABLE, AUTONUMBER) VALUES (?,?,?)".toLowerCase(), fields);
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
	 * Make sure that the site's Melete uploads folder has the proper alternate reference.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void fixUploadColletion(String siteId)
	{
		String id = "/private/meleteDocs/" + siteId + "/uploads/";
		pushAdvisor();
		try
		{
			ContentCollectionEdit uploads = this.contentHostingService.editCollection(id);
			ResourcePropertiesEdit props = uploads.getPropertiesEdit();
			props.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, "/meleteDocs");
			this.contentHostingService.commitCollection(uploads);
		}
		catch (IdUnusedException e)
		{
			M_log.warn("fixUploadColletion: collection not found: " + id);
		}
		catch (TypeException e)
		{
			M_log.warn("fixUploadColletion: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("fixUploadColletion: " + e.toString());
		}
		catch (InUseException e)
		{
			M_log.warn("fixUploadColletion: " + e.toString());
		}
		finally
		{
			popAdvisor();
		}
	}

	@SuppressWarnings("unchecked")
	protected boolean moduleAlreadyExists(Artifact artifact, String userId, String siteId)
	{
		String title = (String) artifact.getProperties().get("title");
		Collection<Map<String, Object>> sectionsColleciton = (Collection<Map<String, Object>>) artifact.getProperties().get("sections");
		int sectionsCollecitonSize = 0;
		if (sectionsColleciton != null) sectionsCollecitonSize = sectionsColleciton.size();

		// get all the modules
		List<ModuleDateBeanService> mdbeans = this.moduleService.getModuleDateBeans(userId, siteId);
		for (ModuleDateBeanService mdbean : mdbeans)
		{
			// title match
			if (different(mdbean.getModule().getTitle(), title)) continue;

			// sections
			Map<Integer, SectionObjService> sections = mdbean.getModule().getSections();

			// # sections match
			int sectionsSize = 0;
			if (sections != null) sectionsSize = sections.size();
			if (sectionsCollecitonSize != sectionsSize) continue;

			// section titles and content type match
			if (sectionsCollecitonSize > 0)
			{
				Iterator<Map<String, Object>> sectionIterator = sectionsColleciton.iterator();
				boolean sectionsMatch = true;
				for (Integer key : sections.keySet())
				{
					Section s = (Section) sections.get(key);
					Map<String, Object> sectionMap = sectionIterator.next();

					if (different(s.getTitle(), sectionMap.get("title")))
					{
						sectionsMatch = false;
						break;
					}

					if (different(s.getContentType(), sectionMap.get("type")))
					{
						sectionsMatch = false;
						break;
					}
				}
				if (!sectionsMatch) continue;
			}

			// we have an existing module that matches title, # sections, and each section's title and type!
			return true;
		}

		// we did not find a match
		return false;
	}

	/**
	 * Remove our security advisor.
	 */
	protected void popAdvisor()
	{
		this.securityService.popAdvisor();
	}

	/**
	 * Setup a security advisor.
	 */
	protected void pushAdvisor()
	{
		// setup a security advisor
		this.securityService.pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});
	}

	/**
	 * Rename a resource in content hosting.
	 * 
	 * @param oldCollectionId
	 *        The old collection id.
	 * @param newCollectionId
	 *        The new collection id.
	 * @param newCollectionName
	 *        The display name for the new collection.
	 * @param oldId
	 *        The old resource id.
	 * @param newId
	 *        The new resource id.
	 * @param newName
	 *        the new resource display name.
	 */
	protected void renameChs(String oldCollectionId, String newCollectionId, String newCollectionName, String oldId, String newId, String newName)
	{
		// bypass security when reading the resource to copy
		this.securityService.pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});

		try
		{
			// create the new collection if needed
			try
			{
				this.contentHostingService.getCollection(newCollectionId);
			}
			catch (IdUnusedException e)
			{
				try
				{
					ContentCollectionEdit edit = this.contentHostingService.addCollection(newCollectionId);
					edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, newCollectionName);
					this.contentHostingService.commitCollection(edit);
				}
				catch (IdUsedException e1)
				{
					M_log.warn("renameChs: " + e.toString());
				}
				catch (IdInvalidException e1)
				{
					M_log.warn("renameChs: " + e.toString());
				}
				catch (PermissionException e1)
				{
					M_log.warn("renameChs: " + e.toString());
				}
				catch (InconsistentException e1)
				{
					M_log.warn("renameChs: " + e.toString());
				}
			}
			catch (TypeException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (PermissionException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}

			// rename the resource
			try
			{
				this.contentHostingService.rename(oldId, newId);

				// update the display name
				ContentResourceEdit edit = this.contentHostingService.editResource(newId);
				edit.getPropertiesEdit().addProperty(ResourceProperties.PROP_DISPLAY_NAME, newName);
				this.contentHostingService.commitResource(edit, 0);
			}
			catch (PermissionException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (IdUnusedException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (TypeException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (InUseException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (OverQuotaException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (InconsistentException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (IdUsedException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (ServerOverloadException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}

			// if the old collection is now empty, we can delete it.
			try
			{
				ContentCollectionEdit edit = this.contentHostingService.editCollection(oldCollectionId);
				try
				{
					this.contentHostingService.removeCollection(edit);
				}
				catch (TypeException e)
				{
					M_log.warn("renameChs: " + e.toString());
				}
				catch (PermissionException e)
				{
					M_log.warn("renameChs: " + e.toString());
				}
				catch (InconsistentException e)
				{
					// we have content...
					this.contentHostingService.cancelCollection(edit);
				}
				catch (ServerOverloadException e)
				{
					M_log.warn("renameChs: " + e.toString());
				}
			}
			catch (IdUnusedException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (TypeException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (PermissionException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
			catch (InUseException e)
			{
				M_log.warn("renameChs: " + e.toString());
			}
		}
		finally
		{
			this.securityService.popAdvisor();
		}
	}

	/**
	 * Check if an entry for this resource exists in Melete's resource table.
	 * 
	 * @param id
	 *        The content hosting id of the resource.
	 * @return true if it exists, false if not.
	 */
	@SuppressWarnings("unchecked")
	protected boolean resourceExists(String id)
	{
		String sql = "SELECT COUNT(1) FROM MELETE_RESOURCE WHERE RESOURCE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<Integer> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int count = result.getInt(1);
					return Integer.valueOf(count);
				}
				catch (SQLException e)
				{
					M_log.warn("resourceExists: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		if (rv.isEmpty()) return false;
		return (rv.get(0).intValue() > 0);
	}

	/**
	 * Set the module as archived.
	 * 
	 * @param id
	 *        The module id.
	 */
	protected void updateModuleArchived(Integer id)
	{
		Object[] fields = new Object[2];
		fields[0] = this.timeService.newTime();
		fields[1] = id;
		write("UPDATE MELETE_COURSE_MODULE SET ARCHV_FLAG = 1, SEQ_NO = -1, DATE_ARCHIVED = ? WHERE MODULE_ID = ?".toLowerCase(), fields);
	}

	/**
	 * Update the module's seqXml with the value archived, modified so that the section ids are translated to the new ids.
	 * 
	 * @param id
	 *        The module id.
	 * @param translations
	 *        The section id translations.
	 * @param seqXml
	 *        The archived seqXml value.
	 */
	protected void updateModuleSeqXml(Integer id, Set<Translation> translations, String seqXml)
	{
		// translate old to new section ids
		for (Translation t : translations)
		{
			seqXml = t.translate(seqXml);
		}

		// update the module record
		Object[] fields = new Object[2];
		fields[0] = seqXml;
		fields[1] = id;
		write("UPDATE MELETE_MODULE SET SEQ_XML = ? WHERE MODULE_ID = ?".toLowerCase(), fields);
	}

	/**
	 * Do a write query.
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void write(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				writeTx(query, fields);
			}
		}, "write: " + fields[0] + " " + query);
	}

	/**
	 * Do a write query (transaction code)
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void writeTx(String query, Object[] fields)
	{
		this.sqlService.dbWriteFailQuiet(null, query, fields);
	}
}
