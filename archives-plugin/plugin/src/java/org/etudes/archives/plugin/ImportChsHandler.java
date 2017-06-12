/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportChsHandler.java $
 * $Id: ImportChsHandler.java 2939 2012-05-21 20:06:57Z ggolden $
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
import org.etudes.util.TranslationImpl;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
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
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives import handler for Content Hosting
 */
public class ImportChsHandler implements ImportHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportChsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.resources";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: EntityManager */
	protected EntityManager entityManager = null;

	/** Dependency: SecurityService */
	protected SecurityService securityService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

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
		M_log.info("import " + applicationId + " in site: " + siteId);

		// have we imported this already?
		for (Translation translation : archive.getTranslations())
		{
			if (translation.getFrom().equals(artifact.getReference()))
			{
				// on this second pass, do the resource body (html) translations (Resources only)
				if (!translation.getTo().endsWith("/"))
				{
					Reference ref = this.entityManager.newReference(translation.getTo());

					// for htmls
					XrefHelper.translateHtmlBody(ref, archive.getTranslations(), siteId, false);

					// for urls
					XrefHelper.translateUrl(ref, archive.getTranslations(), siteId, false);
				}

				return;
			}
		}

		// import this item?
		boolean importItem = false;
		if (toolIds == null)
		{
			importItem = true;
		}
		else
		{
			// /content needs sakai.resources
			if (artifact.getReference().startsWith("/content"))
			{
				if (toolIds.contains("sakai.resources")) importItem = true;
			}
			// /mneme needs sakai.mneme
			else if (artifact.getReference().startsWith("/mneme"))
			{
				if (toolIds.contains("sakai.mneme")) importItem = true;
			}
			// /meleteDocs needs sakai.melete
			else if (artifact.getReference().startsWith("/meleteDocs"))
			{
				if (toolIds.contains("sakai.melete")) importItem = true;
			}
		}

		// import anything that is in the artifact references - these are the references only from the artifacts in the archive that are selected for import
		if (!importItem)
		{
			// Note: we need to be case insensitive here
			for (String ref : archive.getReferences())
			{
				if (ref.equalsIgnoreCase(artifact.getReference()))
				{
					importItem = true;
					break;
				}
			}
		}

		if (!importItem) return;

		// make sure we can read!
		pushAdvisor();
		try
		{
			// merge into the new site
			String destinationRef = null;
			if (artifact.getReference().endsWith("/"))
			{
				destinationRef = importCollection(artifact, siteId, archive);
			}
			else
			{
				destinationRef = importResource(artifact, siteId, archive);
			}

			// make a translation from the old reference to the new reference
			Translation translation = new TranslationImpl(artifact.getReference(), destinationRef);
			archive.getTranslations().add(translation);
		}
		finally
		{
			// clear the security advisor
			popAdvisor();
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
		// import this item?
		boolean importItem = false;
		if (toolIds == null)
		{
			importItem = true;
		}
		else
		{
			// /content needs sakai.resources
			if (artifact.getReference().startsWith("/content"))
			{
				if (toolIds.contains("sakai.resources")) importItem = true;
			}
			// /mneme needs sakai.mneme
			else if (artifact.getReference().startsWith("/mneme"))
			{
				if (toolIds.contains("sakai.mneme")) importItem = true;
			}
			// /meleteDocs needs sakai.melete
			else if (artifact.getReference().startsWith("/meleteDocs"))
			{
				if (toolIds.contains("sakai.melete")) importItem = true;
			}
		}

		// if importing, add the references
		if (importItem)
		{
			archive.getReferences().addAll(artifact.getReferences());
		}
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
	 * Cover to adding a property to a ResourcePropertiesEdit, handling null checks, etc.
	 * 
	 * @param name
	 *        The property name.
	 * @param value
	 *        The value.
	 * @param properties
	 *        The properties.
	 */
	protected void addProp(String name, Object value, ResourcePropertiesEdit properties)
	{
		if (value != null)
		{
			properties.addProperty(name, value.toString());
		}
	}

	/**
	 * Cover to adding a Time property to a ResourcePropertiesEdit, handling null checks, etc.
	 * 
	 * @param name
	 *        The property name.
	 * @param value
	 *        The value.
	 * @param properties
	 *        The properties.
	 */
	protected void addTimeProp(String name, Object value, ResourcePropertiesEdit properties)
	{
		if (value != null)
		{
			properties.addProperty(name, this.timeService.newTime(((Long) value).longValue()).toString());
		}
	}

	/**
	 * Import the collection artifact if we don't find a match in the site
	 * 
	 * @param artifact
	 *        the artifact.
	 * @param siteId
	 *        The destination site id.
	 * @param archive
	 *        The archive.
	 * @return A reference to the new or found collection, or null if it was not found or created.
	 */
	@SuppressWarnings("unchecked")
	protected String importCollection(Artifact artifact, String siteId, Archive archive)
	{
		try
		{
			// get the source information from the artifact
			String ref = artifact.getReference();

			// replace the site id and form the chs ID in the destination
			String[] parts = StringUtil.split(ref, "/");
			int start = 0;
			// /content/group/<old site id>/... -> /content/group/<new site id>/...
			// /content/attachment/<old site id>/... -> /content/attachment/<new site id>/...
			if (("group".equals(parts[2])) || ("attachment".equals(parts[2])))
			{
				parts[3] = siteId;
				start = 2;
			}
			// /mneme/content/private/mneme/<old site id>/... -> /mneme/content/private/<new site id>/...
			// /meleteDocs/content/private/meleteDocs/<old site id>/... -> /meleteDocs/content/private/meleteDocs/<new site id>
			else if ("private".equals(parts[3]))
			{
				parts[5] = siteId;
				start = 3;
			}
			else
			{
				M_log.warn("importResource: unknown reference pattern: " + ref);
			}
			String destCollectionId = "/" + StringUtil.unsplit(parts, start, parts.length - start, "/") + "/";

			try
			{
				// does it exist?
				ContentCollection targetCollection = this.contentHostingService.getCollection(destCollectionId);

				// return this reference
				return targetCollection.getReference();
			}
			catch (IdUnusedException e)
			{
				// there is no collection in the destination
			}

			// fill up the properties from the artifact
			ResourcePropertiesEdit properties = this.contentHostingService.newResourceProperties();
			setProperties(artifact, properties);

			// add the collection
			ContentCollection collection = this.contentHostingService.addCollection(destCollectionId, properties);

			// get it for further editing
			ContentCollectionEdit edit = this.contentHostingService.editCollection(collection.getId());

			// Note: skip "release" and "retract" dates for now

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

					Set<Group> itemGroups = new HashSet<Group>();
					for (String groupTitle : combined)
					{
						for (Group g : groups)
						{
							if (g.getTitle().equals(groupTitle))
							{
								itemGroups.add(g);
								break;
							}
						}
					}

					if (!itemGroups.isEmpty())
					{
						edit.setGroupAccess(itemGroups);
					}
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importArtifact: missing site: " + siteId);
				}
			}

			// save
			this.contentHostingService.commitCollection(edit);

			// return a reference to it
			return collection.getReference();
		}
		catch (PermissionException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (TypeException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdInvalidException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (InconsistentException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdUsedException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdUnusedException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (InUseException e)
		{
			M_log.warn("importResource: " + e.toString());
		}

		return null;
	}

	/**
	 * Import the resource artifact if we don't find a match in the site. Change the name if there's a name conflict.
	 * 
	 * @param artifact
	 *        the artifact.
	 * @param siteId
	 *        The destination site id.
	 * @param archive
	 *        The archive.
	 * @return A reference to the new or found resource, or null if it was not found or created.
	 */
	@SuppressWarnings("unchecked")
	protected String importResource(Artifact artifact, String siteId, Archive archive)
	{
		try
		{
			// get the source information from the artifact
			String ref = artifact.getReference();
			String type = (String) artifact.getProperties().get("type");
			Integer size = (Integer) artifact.getProperties().get("size");

			// replace the site id and form the CHS ID in the destination
			String[] parts = StringUtil.split(ref, "/");
			int start = 0;
			// /content/group/<old site id>/... -> /content/group/<new site id>/...
			// /content/attachment/<old site id>/... -> /content/attachment/<new site id>/...
			if (("group".equals(parts[2])) || ("attachment".equals(parts[2])))
			{
				parts[3] = siteId;
				start = 2;
			}
			// /mneme/content/private/mneme/<old site id>/... -> /mneme/content/private/<new site id>/...
			// /meleteDocs/content/private/meleteDocs/<old site id>/... -> /meleteDocs/content/private/meleteDocs/<new site id>
			else if ("private".equals(parts[3]))
			{
				parts[5] = siteId;
				start = 3;
			}
			else
			{
				M_log.warn("importResource: unknown reference pattern: " + ref);
			}
			String destCollectionId = "/" + StringUtil.unsplit(parts, start, parts.length - (start + 1), "/") + "/";

			// if this exists, don't import - use the existing resource

			// isolate the file name and extension
			String fname = parts[parts.length - 1];
			String extension = null;
			if (fname.indexOf('.') != -1)
			{
				int pos = fname.lastIndexOf('.');
				extension = fname.substring(pos + 1);
				fname = fname.substring(0, pos);
			}

			// the new resource id
			String destinationId = destCollectionId + fname + ((extension == null) ? "" : ("." + extension));

			ContentResource found = null;
			try
			{
				found = this.contentHostingService.getResource(destinationId);
			}
			catch (IdUnusedException e)
			{
			}

			// if we found something with this name, we will use it and not import over it
			if (found != null)
			{
				// return the reference
				return found.getReference();
			}

			// get the body from the artifact
			byte[] body = archive.readFile((String) artifact.getProperties().get("body"), size);

			// give up if we don't have a body
			if ((size == 0) || (body == null) || (body.length == 0))
			{
				M_log.warn("importResource: no body, not importing: " + artifact.getReference());
				return null;
			}

			// fill up the properties from the artifact
			ResourcePropertiesEdit properties = this.contentHostingService.newResourceProperties();
			setProperties(artifact, properties);

			// create the new resource
			ContentResource importedResource = this.contentHostingService.addResource(destinationId, type, body, properties, 0);

			// Note: skip "release" and "retract" dates for now

			ContentResourceEdit edit = this.contentHostingService.editResource(importedResource.getId());

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

					Set<Group> itemGroups = new HashSet<Group>();
					for (String groupTitle : combined)
					{
						for (Group g : groups)
						{
							if (g.getTitle().equals(groupTitle))
							{
								itemGroups.add(g);
								break;
							}
						}
					}

					if (!itemGroups.isEmpty())
					{
						edit.setGroupAccess(itemGroups);
					}
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importArtifact: missing site: " + siteId);
				}
			}

			// save
			this.contentHostingService.commitResource(edit, 0);

			// return a reference to it
			return importedResource.getReference();
		}
		catch (PermissionException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (TypeException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdInvalidException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (InconsistentException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdUsedException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (OverQuotaException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (IdUnusedException e)
		{
			M_log.warn("importResource: " + e.toString());
		}
		catch (InUseException e)
		{
			M_log.warn("importResource: " + e.toString());
		}

		return null;
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
				// pass on userId = "", function = "content.read" - else the CHS group editing functions fail!
				// this is the anon user checking access, and if allowed, means the CHS collection or resource is marked as pub view.
				if (("".equals(userId)) && ("content.read".equals(function))) return SecurityAdvice.PASS;

				return SecurityAdvice.ALLOWED;
			}
		});
	}

	/**
	 * Add optional CHS properties to the artifact.
	 * 
	 * @param artifact
	 *        The artifact.
	 * @param properties
	 *        The properties.
	 */
	protected void setProperties(Artifact artifact, ResourcePropertiesEdit properties)
	{
		// we skip createdById, createdDate, modifiedById, modifiedDate - letting these reset on the import
		// addProp(ResourceProperties.PROP_CREATOR, artifact.getProperties().get("createdById"), properties);
		// addTimeProp(ResourceProperties.PROP_CREATION_DATE, artifact.getProperties().get("createdDate"), properties);
		// addProp(ResourceProperties.PROP_MODIFIED_BY, artifact.getProperties().get("modifiedById"), properties);
		// addTimeProp(ResourceProperties.PROP_MODIFIED_DATE, artifact.getProperties().get("modifiedDate"), properties);
		addProp(ResourceProperties.PROP_DISPLAY_NAME, artifact.getProperties().get("displayName"), properties);
		addProp(ResourceProperties.PROP_ORIGINAL_FILENAME, artifact.getProperties().get("origFileName"), properties);
		addProp(ResourceProperties.PROP_DESCRIPTION, artifact.getProperties().get("description"), properties);
		addProp("http://purl.org/dc/elements/1.1/alternative", artifact.getProperties().get("optAlternateTitle"), properties);
		addProp("http://purl.org/dc/elements/1.1/creator", artifact.getProperties().get("optCreator"), properties);
		addProp("http://purl.org/dc/elements/1.1/publisher", artifact.getProperties().get("optPublisher"), properties);
		addProp("http://purl.org/dc/elements/1.1/subject", artifact.getProperties().get("optSubjectKeywords"), properties);
		addTimeProp("http://purl.org/dc/terms/created", artifact.getProperties().get("optDateCreated"), properties);
		addTimeProp("http://purl.org/dc/terms/issued", artifact.getProperties().get("optDateIssued"), properties);
		addProp("http://purl.org/dc/terms/abstract", artifact.getProperties().get("optAbstract"), properties);
		addProp("http://purl.org/dc/elements/1.1/contributor", artifact.getProperties().get("optContributor"), properties);
		addProp("http://purl.org/dc/terms/audience", artifact.getProperties().get("optAudience"), properties);
		addProp("http://purl.org/dc/terms/educationLevel", artifact.getProperties().get("optAudienceEducationLevel"), properties);
		addProp("CHEF:copyrightchoice", artifact.getProperties().get("copyrightChoice"), properties);
		addProp("CHEF:copyright", artifact.getProperties().get("copyright"), properties);
		addProp("CHEF:copyrightalert", artifact.getProperties().get("copyrightAlert"), properties);
		addProp(ContentHostingService.PROP_ALTERNATE_REFERENCE, artifact.getProperties().get("altRef"), properties);
		addProp("attachment:thumb", artifact.getProperties().get("mnemeThumb"), properties);
		addProp("attachment:unique", artifact.getProperties().get("mnemeUnique"), properties);
	}

	/**
	 * Translate the resource from the source site to the destination site.
	 * 
	 * @param ref
	 *        The resource reference.
	 * @param sourceSiteId
	 *        The source site id.
	 * @param destSiteId
	 *        The destination site id.
	 * @return The translation, or null if we don't need one.
	 */
	protected Translation translateToSite(String ref, String sourceSiteId, String destSiteId)
	{
		String destRef = ref;

		// /content/group/<old site id>/... -> /content/group/<new site id>/...
		if (destRef.startsWith("/content/group/" + sourceSiteId + "/"))
		{
			destRef = "/content/group/" + destSiteId + "/" + destRef.substring("/content/group/".length() + sourceSiteId.length() + "/".length());
		}

		// /content/attachment/<old site id>/... -> /content/attachment/<new site id>/...
		else if (destRef.startsWith("/content/attachment/" + sourceSiteId + "/"))
		{
			destRef = "/content/attachment/" + destSiteId + "/"
					+ destRef.substring("/content/attachment/".length() + sourceSiteId.length() + "/".length());
		}

		// /mneme/content/private/mneme/<old site id>/... -> /mneme/content/private/<new site id>/...
		else if (destRef.startsWith("/mneme/content/private/mneme/" + sourceSiteId + "/"))
		{
			destRef = "/mneme/content/private/mneme/" + destSiteId + "/"
					+ destRef.substring("/mneme/content/private/mneme/".length() + sourceSiteId.length() + "/".length());
		}

		// /meleteDocs/content/private/meleteDocs/<old site id>/... -> /meleteDocs/content/private/meleteDocs/<new site id>
		else if (destRef.startsWith("/meleteDocs/content/private/meleteDocs/" + sourceSiteId + "/"))
		{
			destRef = "/meleteDocs/content/private/meleteDocs/" + destSiteId + "/"
					+ destRef.substring("/meleteDocs/content/private/meleteDocs/".length() + sourceSiteId.length() + "/".length());
		}

		if (destRef.equals(ref)) return null;

		return new TranslationImpl(ref, destRef);
	}
}
