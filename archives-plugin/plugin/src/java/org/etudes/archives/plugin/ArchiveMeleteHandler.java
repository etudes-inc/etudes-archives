/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveMeleteHandler.java $
 * $Id: ArchiveMeleteHandler.java 12133 2015-11-25 15:53:41Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2015 Etudes, Inc.
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

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.mneme.api.Attachment;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.util.XrefHelper;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives archive handler for Melete
 */
public class ArchiveMeleteHandler implements ArchiveHandler
{
	protected class Module
	{
		Boolean archived;
		Long created;
		String description;
		Long end;
		Long allowUntil;
		Boolean hideUntilOpen;
		Long id;
		String keywords;
		Long modified;
		String next;
		Long seq;
		String seqXml;
		Long start;
		String title;
		String userId;
	}

	protected class Preferences
	{
		Boolean autoNumbering;
		Boolean studentPrinting;
	}

	protected class Resource
	{
		Integer allowModification;
		Boolean attribution;
		String ccLicense;
		Boolean commercial;
		String copyrightOwner;
		String copyrightYear;
		Integer license;
		String ref;
	}

	protected class Section
	{
		Boolean audio;
		Long id;
		String instructions;
		Resource resource;
		Long seq;
		Boolean textual;
		String title;
		String type;
		Boolean video;
		Boolean window;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveMeleteHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.melete";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AttachmentService */
	protected AttachmentService attachmentService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: EntityManager. */
	protected EntityManager entityManager = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// docs - all of them, even if not referenced
		List<Attachment> allDocs = this.attachmentService.findFiles("meleteDocs", siteId, "uploads");
		if (!allDocs.isEmpty())
		{
			Artifact artifact = archive.newArtifact(applicationId, "/docs");

			// cover all documents in Mneme Docs, even if not referenced
			Set<String> refs = new HashSet<String>();
			for (Attachment attachment : allDocs)
			{
				refs.add(attachment.getReference());
			}

			artifact.getReferences().addAll(refs);
			archive.archive(artifact);
		}

		// read the module data for the site
		List<Module> modules = readModules(siteId);
		for (Module m : modules)
		{
			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, "/module/" + m.id.toString());
			artifact.getProperties().put("id", m.id);
			artifact.getProperties().put("title", m.title);
			artifact.getProperties().put("description", m.description);
			artifact.getProperties().put("keywords", m.keywords);
			artifact.getProperties().put("userId", m.userId);
			try
			{
				artifact.getProperties().put("user", this.userDirectoryService.getUser(m.userId).getDisplayName());
			}
			catch (UserNotDefinedException e)
			{
			}
			artifact.getProperties().put("created", m.created);
			artifact.getProperties().put("modified", m.modified);
			artifact.getProperties().put("next", m.next);
			artifact.getProperties().put("start", m.start);
			artifact.getProperties().put("end", m.end);
			artifact.getProperties().put("allowUntil", m.allowUntil);
			artifact.getProperties().put("hideUntilOpen", m.hideUntilOpen);
			artifact.getProperties().put("seq", m.seq);
			artifact.getProperties().put("archived", m.archived);
			artifact.getProperties().put("seqXml", m.seqXml);

			// sections - collection of map
			Collection<Map<String, Object>> sectionColleciton = new ArrayList<Map<String, Object>>();
			List<Section> sections = this.readSections(m.id);
			setSectionSeq(sections, m.seqXml);
			for (Section section : sections)
			{
				Map<String, Object> sectionMap = new HashMap<String, Object>();
				sectionColleciton.add(sectionMap);
				sectionMap.put("id", section.id);
				sectionMap.put("audio", section.audio);
				sectionMap.put("instructions", section.instructions);
				sectionMap.put("seq", section.seq);
				sectionMap.put("textual", section.textual);
				sectionMap.put("title", section.title);
				sectionMap.put("type", section.type);
				sectionMap.put("video", section.video);
				sectionMap.put("window", section.window);

				Map<String, Object> resourceMap = new HashMap<String, Object>();
				resourceMap.put("allowModification", section.resource.allowModification);
				resourceMap.put("reqAttribution", section.resource.attribution);
				resourceMap.put("ccLicense", section.resource.ccLicense);
				resourceMap.put("commercial", section.resource.commercial);
				resourceMap.put("copyrightOwner", section.resource.copyrightOwner);
				resourceMap.put("copyrightYear", section.resource.copyrightYear);
				resourceMap.put("license", section.resource.license);
				resourceMap.put("ref", section.resource.ref);

				sectionMap.put("resource", resourceMap);

				if (section.resource.ref != null)
				{
					// get the embedded references in this resource
					Set<String> refs = this.getEmbeddedReferences(section.resource.ref);

					// we need resource for these refs
					List<Map<String, Object>> embeddedRefs = new ArrayList<Map<String, Object>>();
					for (String embeddedRef : refs)
					{
						String id = embeddedRef.substring(embeddedRef.indexOf("/content") + "/content".length());
						Resource resource = readResource(id);
						if (resource != null)
						{
							Map<String, Object> embeddedMap = new HashMap<String, Object>();
							embeddedMap.put("allowModification", resource.allowModification);
							embeddedMap.put("reqAttribution", resource.attribution);
							embeddedMap.put("ccLicense", resource.ccLicense);
							embeddedMap.put("commercial", resource.commercial);
							embeddedMap.put("copyrightOwner", resource.copyrightOwner);
							embeddedMap.put("copyrightYear", resource.copyrightYear);
							embeddedMap.put("license", resource.license);
							embeddedMap.put("ref", resource.ref);

							embeddedRefs.add(embeddedMap);
						}
					}
					if (!embeddedRefs.isEmpty())
					{
						sectionMap.put("resources", embeddedRefs);
					}

					// add the embedded references as artifact references - do this before the embedding ref
					artifact.getReferences().addAll(refs);

					// add this as an artifact reference
					artifact.getReferences().add(section.resource.ref);
				}
			}
			artifact.getProperties().put("sections", sectionColleciton);

			// archive it
			archive.archive(artifact);
		}

		// site preferences
		Preferences prefs = readPreferences(siteId);
		if (prefs != null)
		{
			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, "/prefs");
			artifact.getProperties().put("studentPrinting", prefs.studentPrinting);
			artifact.getProperties().put("autoNumbering", prefs.autoNumbering);

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
	 * Dependency: AttachmentService.
	 * 
	 * @param service
	 *        The AttachmentService.
	 */
	public void setAttachmentService(AttachmentService service)
	{
		attachmentService = service;
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
	 * Set the EntityManager.
	 * 
	 * @param service
	 *        The EntityManager.
	 */
	public void setEntityManager(EntityManager service)
	{
		this.entityManager = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
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
	 * Check the html resources referenced by the artifact for embedded resources from the site, adding them to the artifact's references.
	 * 
	 * @param siteId
	 *        the site id.
	 * @param artifact
	 *        The artifact.
	 */
	protected void addEmbeddedReferences(String siteId, Artifact artifact)
	{
		Set<String> toAdd = new HashSet<String>();
		for (String ref : artifact.getReferences())
		{
			// get an id from the reference string
			Reference reference = this.entityManager.newReference(ref);
			String id = reference.getId();
			if (id.startsWith("/content/"))
			{
				id = id.substring("/content".length());
			}

			// get the resource
			try
			{
				ContentResource r = this.contentHostingService.getResource(id);

				// only for html
				if ("text/html".equals(r.getContentType()))
				{
					byte[] body = r.getContent();
					if (body != null)
					{
						String text = new String(body, "UTF-8");

						// get embedded references
						Set<String> refs = XrefHelper.harvestEmbeddedReferences(text, null);

						// collect to the artifact
						toAdd.addAll(refs);
					}
				}
			}
			catch (IOException e)
			{
				M_log.warn("addEmbeddedReferences: " + e.toString());
			}
			catch (IdUnusedException e)
			{
			}
			catch (TypeException e)
			{
				M_log.warn("addEmbeddedReferences: " + e.toString());
			}
			catch (PermissionException e)
			{
				M_log.warn("addEmbeddedReferences: " + e.toString());
			}
			catch (ServerOverloadException e)
			{
				M_log.warn("addEmbeddedReferences: " + e.toString());
			}
		}

		artifact.getReferences().addAll(toAdd);
	}

	/**
	 * Get the html resources referenced by the referenced resources.
	 * 
	 * @param ref
	 *        The resource reference.
	 */
	protected Set<String> getEmbeddedReferences(String ref)
	{
		// get an id from the reference string
		Reference reference = this.entityManager.newReference(ref);
		String id = reference.getId();
		if (id.startsWith("/content/"))
		{
			id = id.substring("/content".length());
		}

		// get the resource
		try
		{
			ContentResource r = this.contentHostingService.getResource(id);

			// only for html
			if ("text/html".equals(r.getContentType()))
			{
				byte[] body = r.getContent();
				if (body == null) return new LinkedHashSet<String>();

				String text = new String(body, "UTF-8");

				Set<String> refs = XrefHelper.harvestEmbeddedReferences(text, null);
				return refs;
			}
		}
		catch (IOException e)
		{
			M_log.warn("getEmbeddedReferences: " + e.toString());
		}
		catch (IdUnusedException e)
		{
		}
		catch (TypeException e)
		{
			M_log.warn("getEmbeddedReferences: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("getEmbeddedReferences: " + e.toString());
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("getEmbeddedReferences: " + e.toString());
		}

		return new HashSet<String>();
	}

	/**
	 * Make an integer from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The integer.
	 */
	protected Integer integerValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Integer.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Make a long from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The long.
	 */
	protected Long longValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Long.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Read modules items for this site.
	 * 
	 * @param siteId
	 *        The context site Id
	 * @return List<Module> for the modules found in the site.
	 */
	@SuppressWarnings("unchecked")
	protected List<Module> readModules(final String siteId)
	{
		String sql = "SELECT M.MODULE_ID, M.TITLE, M.DESCRIPTION, M.KEYWORDS, M.USER_ID, M.WHATS_NEXT, M.CREATION_DATE, M.MODIFICATION_DATE,"
				+ " D.START_DATE, D.END_DATE, D.ALLOWUNTIL_DATE, D.HIDE_UNTIL_START, C.SEQ_NO, M.SEQ_XML, C.ARCHV_FLAG                                                             "
				+ " FROM MELETE_MODULE M                                                                                                    "
				+ " LEFT OUTER JOIN MELETE_MODULE_SHDATES D ON M.MODULE_ID = D.MODULE_ID                                                    "
				+ " INNER JOIN MELETE_COURSE_MODULE C ON M.MODULE_ID = C.MODULE_ID                                                          "
				+ " WHERE C.COURSE_ID = ? AND DELETE_FLAG = 0 ORDER BY C.SEQ_NO ASC";
		Object[] fields = new Object[1];
		fields[0] = siteId;
		final SqlService ss = this.sqlService;

		List<Module> rv = this.sqlService.dbRead(sql.toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Module m = new Module();
					m.id = longValue(StringUtil.trimToNull(result.getString(1)));
					m.title = StringUtil.trimToNull(result.getString(2));
					m.description = StringUtil.trimToNull(result.getString(3));
					m.keywords = StringUtil.trimToNull(result.getString(4));
					m.userId = StringUtil.trimToNull(result.getString(5));
					m.next = StringUtil.trimToNull(result.getString(6));
					Timestamp ts = result.getTimestamp(7, ss.getCal());
					m.created = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(8, ss.getCal());
					m.modified = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(9, ss.getCal());
					m.start = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(10, ss.getCal());
					m.end = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(11, ss.getCal());
					m.allowUntil = (ts == null) ? null : Long.valueOf(ts.getTime());
					m.hideUntilOpen = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(12))));
					m.seq = longValue(StringUtil.trimToNull(result.getString(13)));
					m.seqXml = StringUtil.trimToNull(result.getString(14));
					m.archived = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(15))));

					return m;

				}
				catch (SQLException e)
				{
					M_log.warn("readModules: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Read site preferences for Melete
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The Resource with this resource's information.
	 */
	@SuppressWarnings("unchecked")
	protected Preferences readPreferences(final String siteId)
	{
		String sql = "SELECT P.PRINTABLE, P.AUTONUMBER FROM MELETE_SITE_PREFERENCE P WHERE P.PREF_SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Preferences> rv = this.sqlService.dbRead(sql.toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Preferences prefs = new Preferences();
					prefs.studentPrinting = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(1))));
					prefs.autoNumbering = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(2))));

					return prefs;
				}
				catch (SQLException e)
				{
					M_log.warn("readPreferences: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		if (rv.isEmpty()) return null;
		return rv.get(0);
	}

	/**
	 * Read a resource..
	 * 
	 * @param resourceId
	 *        The resource id.
	 * @return The Resource with this resource's information.
	 */
	@SuppressWarnings("unchecked")
	protected Resource readResource(final String resourceId)
	{
		String sql = "SELECT R.RESOURCE_ID, R.LICENSE_CODE, R.CC_LICENSE_URL, R.REQ_ATTR, R.ALLOW_CMRCL, R.ALLOW_MOD, R.COPYRIGHT_OWNER, R.COPYRIGHT_YEAR"
				+ " FROM MELETE_RESOURCE R WHERE R.RESOURCE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = resourceId;

		List<Resource> rv = this.sqlService.dbRead(sql.toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Resource resource = new Resource();
					resource.ref = StringUtil.trimToNull(result.getString(1));
					if (resource.ref != null) resource.ref = "/meleteDocs/content" + resource.ref;
					resource.license = integerValue(StringUtil.trimToNull(result.getString(2)));
					resource.ccLicense = StringUtil.trimToNull(result.getString(3));
					resource.attribution = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(4))));
					resource.commercial = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(5))));
					resource.allowModification = integerValue(StringUtil.trimToNull(result.getString(6)));
					resource.copyrightOwner = StringUtil.trimToNull(result.getString(7));
					resource.copyrightYear = StringUtil.trimToNull(result.getString(8));

					return resource;

				}
				catch (SQLException e)
				{
					M_log.warn("readResource: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		if (rv.isEmpty()) return null;
		return rv.get(0);
	}

	/**
	 * Read sections for this module.
	 * 
	 * @param moduleId
	 *        The module id.
	 * @return List<Section> for the sections for this module.
	 */
	@SuppressWarnings("unchecked")
	protected List<Section> readSections(final Long moduleId)
	{
		String sql = "SELECT S.SECTION_ID, S.TITLE, S.INSTR, S.CONTENT_TYPE, S.AUDIO_CONTENT, S.VIDEO_CONTENT, S.TEXTUAL_CONTENT, S.OPEN_WINDOW,"
				+ " R.RESOURCE_ID, R.LICENSE_CODE, R.CC_LICENSE_URL, R.REQ_ATTR, R.ALLOW_CMRCL, R.ALLOW_MOD, R.COPYRIGHT_OWNER, R.COPYRIGHT_YEAR"
				+ " FROM MELETE_SECTION S                                                                                                       "
				+ " LEFT OUTER JOIN MELETE_SECTION_RESOURCE SR ON S.SECTION_ID = SR.SECTION_ID                                                  "
				+ " LEFT OUTER JOIN MELETE_RESOURCE R ON SR.RESOURCE_ID = R.RESOURCE_ID                                                         "
				+ " WHERE S.MODULE_ID = ? AND S.DELETE_FLAG = 0";
		Object[] fields = new Object[1];
		fields[0] = moduleId;

		List<Section> rv = this.sqlService.dbRead(sql.toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Section m = new Section();
					m.id = longValue(StringUtil.trimToNull(result.getString(1)));
					m.title = StringUtil.trimToNull(result.getString(2));
					m.instructions = StringUtil.trimToNull(result.getString(3));
					m.type = StringUtil.trimToNull(result.getString(4));
					m.audio = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(5))));
					m.video = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(6))));
					m.textual = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(7))));
					m.window = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(8))));

					m.resource = new Resource();
					m.resource.ref = StringUtil.trimToNull(result.getString(9));
					if (m.resource.ref != null) m.resource.ref = "/meleteDocs/content" + m.resource.ref;
					m.resource.license = integerValue(StringUtil.trimToNull(result.getString(10)));
					m.resource.ccLicense = StringUtil.trimToNull(result.getString(11));
					m.resource.attribution = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(12))));
					m.resource.commercial = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(13))));
					m.resource.allowModification = integerValue(StringUtil.trimToNull(result.getString(14)));
					m.resource.copyrightOwner = StringUtil.trimToNull(result.getString(15));
					m.resource.copyrightYear = StringUtil.trimToNull(result.getString(16));

					return m;

				}
				catch (SQLException e)
				{
					M_log.warn("readSections: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Using the sequence XML from the module, set the sequence number (1 based) of each section.
	 * 
	 * @param sections
	 *        The sections.
	 * @param seqXml
	 *        The sequence xml.
	 */
	protected void setSectionSeq(List<Section> sections, String seqXml)
	{
		if (seqXml == null) return;

		Pattern p = Pattern.compile("section id=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

		int seq = 1;
		Matcher m = p.matcher(seqXml);
		while (m.find())
		{
			if (m.groupCount() == 1)
			{
				String str = m.group(1);
				Long id = Long.parseLong(str);

				for (Section section : sections)
				{
					if (section.id.equals(id))
					{
						section.seq = Long.valueOf(seq);
					}
				}
				seq++;
			}
		}
	}
}
