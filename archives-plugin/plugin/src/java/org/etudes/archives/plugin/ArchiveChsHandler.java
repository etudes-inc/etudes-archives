/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveChsHandler.java $
 * $Id: ArchiveChsHandler.java 2959 2012-05-31 18:24:56Z ggolden $
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
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

/**
 * Archives archive handler for Content Hosting
 */
public class ArchiveChsHandler implements ArchiveHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveChsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.resources";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// site's main resources area is /group/site id/
		String root = "/group/" + siteId + "/";

		// get the list all the resources and collections for the site, all the way deep
		List<String> references = getAllMembers(root);

		// merge in the set of attachments needed by the archive
		Set<String> others = archive.getReferences();
		for (String other : others)
		{
			if (!references.contains(other))
			{
				references.add(other);
			}
		}

		// archive the collections
		for (String ref : references)
		{
			if (ref.endsWith("/"))
			{
				archiveCollection(archive, ref);
			}
		}

		// archive the resources
		for (String ref : references)
		{
			if (!ref.endsWith("/"))
			{
				archiveResource(archive, ref);
			}
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
	 * Archive a collection.
	 * 
	 * @param archive
	 *        The archive.
	 * @param ref
	 *        The collection reference.
	 */
	@SuppressWarnings("unchecked")
	protected void archiveCollection(Archive archive, String ref)
	{
		try
		{
			ContentCollection c = this.contentHostingService.getCollection(ref.substring("/content".length()));

			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, c.getReference());

			// set the collection information into the properties
			artifact.getProperties().put("ref", c.getReference());
			archiveProperties(artifact, c.getProperties());
			archiveGroups(artifact, c.getGroupObjects());
			artifact.getProperties().put("access", c.getAccess().toString());
			if (c.getReleaseDate() != null) artifact.getProperties().put("release", Long.valueOf(c.getReleaseDate().getTime()));
			if (c.getRetractDate() != null) artifact.getProperties().put("retract", Long.valueOf(c.getRetractDate().getTime()));

			// archive it
			archive.archive(artifact);
		}
		catch (IdUnusedException e)
		{
		}
		catch (TypeException e)
		{
			M_log.warn("archiveCollection: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("archiveCollection: " + e.toString());
		}
	}

	/**
	 * Archive the group settings
	 * 
	 * @param artifact
	 *        The artifact.
	 * @param groups
	 *        The groups.
	 */
	protected void archiveGroups(Artifact artifact, Collection<Group> groups)
	{
		// get any groups and sections as group titles
		List<String> groupTitles = new ArrayList<String>();
		List<String> sectionTitles = new ArrayList<String>();
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
	}

	/**
	 * Add optional CHS properties to the artifact.
	 * 
	 * @param artifact
	 *        The artifact.
	 * @param properties
	 *        The properties.
	 */
	protected void archiveProperties(Artifact artifact, ResourceProperties properties)
	{
		User createdUser = getUserProperty(properties, ResourceProperties.PROP_CREATOR);
		if (createdUser != null)
		{
			artifact.getProperties().put("createdById", createdUser.getId());
			artifact.getProperties().put("createdBy", createdUser.getDisplayName());
		}

		try
		{
			artifact.getProperties().put("createdDate", properties.getTimeProperty(ResourceProperties.PROP_CREATION_DATE).getTime());
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		catch (EntityPropertyTypeException e)
		{
		}

		User modifiedUser = getUserProperty(properties, ResourceProperties.PROP_MODIFIED_BY);
		if (modifiedUser != null)
		{
			artifact.getProperties().put("modifiedById", modifiedUser.getId());
			artifact.getProperties().put("modifiedBy", modifiedUser.getDisplayName());
		}

		try
		{
			artifact.getProperties().put("modifiedDate", properties.getTimeProperty(ResourceProperties.PROP_MODIFIED_DATE).getTime());
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		catch (EntityPropertyTypeException e)
		{
		}

		artifact.getProperties().put("displayName", properties.getProperty(ResourceProperties.PROP_DISPLAY_NAME));
		artifact.getProperties().put("origFileName", properties.getProperty(ResourceProperties.PROP_ORIGINAL_FILENAME));
		artifact.getProperties().put("description", properties.getProperty(ResourceProperties.PROP_DESCRIPTION));

		artifact.getProperties().put("optAlternateTitle", properties.getProperty("http://purl.org/dc/elements/1.1/alternative"));
		artifact.getProperties().put("optCreator", properties.getProperty("http://purl.org/dc/elements/1.1/creator"));
		artifact.getProperties().put("optPublisher", properties.getProperty("http://purl.org/dc/elements/1.1/publisher"));
		artifact.getProperties().put("optSubjectKeywords", properties.getProperty("http://purl.org/dc/elements/1.1/subject"));
		try
		{
			artifact.getProperties().put("optDateCreated", properties.getTimeProperty("http://purl.org/dc/terms/created").getTime());
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		catch (EntityPropertyTypeException e)
		{
		}
		try
		{
			artifact.getProperties().put("optDateIssued", properties.getTimeProperty("http://purl.org/dc/terms/issued").getTime());
		}
		catch (EntityPropertyNotDefinedException e)
		{
		}
		catch (EntityPropertyTypeException e)
		{
		}
		artifact.getProperties().put("optAbstract", properties.getProperty("http://purl.org/dc/terms/abstract"));
		artifact.getProperties().put("optContributor", properties.getProperty("http://purl.org/dc/elements/1.1/contributor"));
		artifact.getProperties().put("optAudience", properties.getProperty("http://purl.org/dc/terms/audience"));
		artifact.getProperties().put("optAudienceEducationLevel", properties.getProperty("http://purl.org/dc/terms/educationLevel"));

		artifact.getProperties().put("copyrightChoice", properties.getProperty("CHEF:copyrightchoice"));
		artifact.getProperties().put("copyright", properties.getProperty("CHEF:copyright"));
		artifact.getProperties().put("copyrightAlert", properties.getProperty("CHEF:copyrightalert"));

		artifact.getProperties().put("altRef", properties.getProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE));

		artifact.getProperties().put("mnemeThumb", properties.getProperty("attachment:thumb"));
		artifact.getProperties().put("mnemeUnique", properties.getProperty("attachment:unique"));
	}

	/**
	 * Archive a resource.
	 * 
	 * @param archive
	 *        The archive.
	 * @param ref
	 *        The resource reference.
	 */
	@SuppressWarnings("unchecked")
	protected void archiveResource(Archive archive, String ref)
	{
		try
		{
			ContentResource r = this.contentHostingService.getResource(ref.substring(ref.indexOf("/content") + "/content".length()));

			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, r.getReference());

			// set the resource information into the properties
			artifact.getProperties().put("ref", r.getReference());
			artifact.getProperties().put("type", r.getContentType());
			artifact.getProperties().put("size", Integer.valueOf(r.getContentLength()));
			if (r.getReleaseDate() != null) artifact.getProperties().put("release", Long.valueOf(r.getReleaseDate().getTime()));
			if (r.getRetractDate() != null) artifact.getProperties().put("retract", Long.valueOf(r.getRetractDate().getTime()));
			archiveProperties(artifact, r.getProperties());
			archiveGroups(artifact, r.getGroupObjects());

			// the body
			artifact.getProperties().put("body", r.streamContent());

			// archive it
			archive.archive(artifact);
		}
		catch (IdUnusedException e)
		{
		}
		catch (TypeException e)
		{
			M_log.warn("archiveResource: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("archiveResource: " + e.toString());
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("archiveResource: " + e.toString());
		}
	}

	/**
	 * Get the reference string of all members, all the way down to contained collections. Order so that a collection is listed before it's contained.
	 * 
	 * @param root
	 *        The root collection.
	 * @return A List of the member references.
	 */
	@SuppressWarnings("unchecked")
	protected List<String> getAllMembers(String root)
	{
		List<String> rv = new ArrayList<String>();

		try
		{
			ContentCollection c = this.contentHostingService.getCollection(root);
			rv.add(lowerCase(c.getReference()));

			List<String> members = c.getMembers();
			for (String member : members)
			{
				// for collections
				if (member.endsWith("/"))
				{
					rv.addAll(getAllMembers(member));
				}

				// for resources
				else
				{
					rv.add(lowerCase("/content" + member));
				}
			}
		}
		catch (IdUnusedException e)
		{
			// no collection, no members
			// M_log.warn("getAllMembers: " + e.toString());
		}
		catch (TypeException e)
		{
			M_log.warn("getAllMembers: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("getAllMembers: " + e.toString());
		}

		return rv;
	}

	/**
	 * Read a User object from a named property.
	 * 
	 * @param props
	 *        The properties.
	 * @param name
	 *        The property name.
	 * @return The user if found, or null.
	 */
	protected User getUserProperty(ResourceProperties props, String name)
	{
		String id = props.getProperty(name);
		if (id != null)
		{
			try
			{
				return this.userDirectoryService.getUser(id);
			}
			catch (UserNotDefinedException e)
			{
			}
		}

		return null;
	}

	/**
	 * Properly lower case a CHS reference.
	 * 
	 * @param ref
	 *        The reference.
	 * @return The lower cased reference.
	 */
	protected String lowerCase(String ref)
	{
		// if we start with /content/private/meleteDocs, we need the capital D - everything else can be lowered
		String rv = ref.toLowerCase();

		if (rv.startsWith("/content/private/meletedocs"))
		{
			rv = "/content/private/meleteDocs" + rv.substring("/content/private/meleteDocs".length());
		}

		return rv;
	}
}
