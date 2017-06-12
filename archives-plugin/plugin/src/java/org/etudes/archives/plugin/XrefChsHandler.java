/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefChsHandler.java $
 * $Id: XrefChsHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;

/**
 * Archives cross reference handler for Resources
 */
public class XrefChsHandler implements XrefHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefChsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.resources";

	/** Application code for Melete in ContentHosting's private area. */
	static final String MELETE_APPLICATION = "meleteDocs";

	/** Alternate reference for melete docs. */
	static final String MELETE_REFERENCE_ROOT = "/meleteDocs";

	/** Prefix for the Melete uploads area. */
	static final String UPLOADS_AREA = "uploads";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: AttachmentService */
	protected AttachmentService attachmentService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: EntityManager */
	protected EntityManager entityManager = null;

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
		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		int count = 0;

		// for a user site
		if (siteId.startsWith("~"))
		{
			// /user/siteId w/o ~ only
			String root = "/user/" + siteId.substring(1) + "/";
			count += removeXref(siteId, root);
			return count;
		}

		// site's main resources area is /group/site id/
		String root = "/group/" + siteId + "/";
		count += removeXref(siteId, root);

		// do Mneme's private docs
		root = "/private/mneme/" + siteId + "/docs/";
		count += removeXref(siteId, root);

		// do Melete's private docs
		root = "/private/meleteDocs/" + siteId + "/";
		count += removeXref(siteId, root);

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
	 * Dependency: AttachmentService.
	 * 
	 * @param service
	 *        The AttachmentService.
	 */
	public void setAttachmentService(AttachmentService service)
	{
		this.attachmentService = service;
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
	 * Get the reference string of all members, all the way down to contained collections. Order so that a collection is listed before it's contained.
	 * 
	 * @param root
	 *        The root collection.
	 * @return A List of the member references.
	 */
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

	/**
	 * Read a document from content hosting.
	 * 
	 * @param ref
	 *        The document reference.
	 * @return The document content in a String.
	 */
	protected String readReferencedDocument(String ref)
	{
		// get an id from the reference string
		Reference reference = this.entityManager.newReference(ref);
		String id = reference.getId();
		if (id.startsWith("/content/"))
		{
			id = id.substring("/content".length());
		}

		try
		{
			// read the resource
			ContentResource r = this.contentHostingService.getResource(id);

			// get the body into a string
			byte[] body = r.getContent();
			if (body == null) return null;

			String bodyString = new String(body, "UTF-8");

			return bodyString;
		}
		catch (IOException e)
		{
			M_log.warn("readReferencedDocument: " + e.toString());
		}
		catch (IdUnusedException e)
		{
		}
		catch (TypeException e)
		{
			M_log.warn("readReferencedDocument: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("readReferencedDocument: " + e.toString());
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("readReferencedDocument: " + e.toString());
		}

		return "";
	}

	/**
	 * Read a document's mime type from content hosting.
	 * 
	 * @param ref
	 *        The document reference.
	 * @return The document's mime type.
	 */
	protected String readReferencedDocumentType(String ref)
	{
		// bypass security when reading the resource to copy
		SecurityService.pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});

		try
		{
			// get an id from the reference string
			Reference reference = this.entityManager.newReference(ref);
			String id = reference.getId();
			if (id.startsWith("/content/"))
			{
				id = id.substring("/content".length());
			}

			try
			{
				// read the resource
				ContentResource r = this.contentHostingService.getResource(id);
				String type = r.getContentType();

				return type;
			}
			catch (IdUnusedException e)
			{
			}
			catch (TypeException e)
			{
				M_log.warn("readReferencedDocumentType: " + e.toString());
			}
			catch (PermissionException e)
			{
				M_log.warn("readReferencedDocumentType: " + e.toString());
			}
		}
		finally
		{
			SecurityService.popAdvisor();
		}

		return "";
	}

	/**
	 * Remove the cross references in all the resources under this root collection.
	 * 
	 * @param siteId
	 *        The site.
	 * @param root
	 *        The root collection reference.
	 * @return The number of embedded references found.
	 */
	protected int removeXref(String siteId, String root)
	{
		int count = 0;

		// get the list all the resources and collections for the site, all the way deep
		List<String> references = getAllMembers(root);

		// find any embedded references outside of the site
		Set<String> embedded = new HashSet<String>();
		for (String ref : references)
		{
			String type = readReferencedDocumentType(ref);
			if ("text/html".equals(type))
			{
				// get the body
				String body = readReferencedDocument(ref);

				// get the embedded references, deep
				embedded.addAll(XrefHelper.harvestEmbeddedReferences(body, siteId));
			}

			// for Melete's link documents
			else if ((root.startsWith("/private/meleteDocs/")) && ("text/url".equals(type)))
			{
				// Note: since the body is a raw URL, not html, and XrefHelper is geared so well for html, we will cheat a little here -ggolden

				// get the body
				String body = readReferencedDocument(ref);

				// wrap with something that the XrefHelper will recognize
				body = "src=\"" + body + "\"";

				// get the embedded references, deep
				embedded.addAll(XrefHelper.harvestEmbeddedReferences(body, siteId));
			}
		}

		count += embedded.size();

		// import
		List<Translation> translations = null;
		if (root.startsWith("/private/mneme/"))
		{
			translations = this.attachmentService.importResources(AttachmentService.MNEME_APPLICATION, siteId, AttachmentService.DOCS_AREA,
					AttachmentService.NameConflictResolution.keepExisting, embedded, AttachmentService.MNEME_THUMB_POLICY,
					AttachmentService.REFERENCE_ROOT);
		}
		else if (root.startsWith("/private/meleteDocs/"))
		{
			translations = this.attachmentService.importResources(MELETE_APPLICATION, siteId, UPLOADS_AREA,
					AttachmentService.NameConflictResolution.keepExisting, embedded, false, MELETE_REFERENCE_ROOT);
		}
		else
		{
			translations = XrefHelper.importTranslateResources(embedded, siteId, "Resources");
		}

		// translate and shorten
		for (String ref : references)
		{
			String type = readReferencedDocumentType(ref);
			if ("text/html".equals(type))
			{
				Reference reference = this.entityManager.newReference(ref);
				XrefHelper.translateHtmlBody(reference, translations, siteId, true);
			}

			// for Melete's link documents
			else if ((root.startsWith("/private/meleteDocs/")) && ("text/url".equals(type)))
			{
				// Note: cheating again -ggolden

				// get the body
				String body = readReferencedDocument(ref);

				// wrap with something that the XrefHelper will recognize
				body = "src=\"" + body + "\"";

				// translate and shorten
				String newBodyString = XrefHelper.translateEmbeddedReferencesAndShorten(body, translations, siteId, null);

				// if changed
				if (!body.equals(newBodyString))
				{
					// pull it out of the wrapper
					String newUrl = newBodyString.substring(5, newBodyString.length() - 1);

					// update the resource
					updateResource(ref, newUrl);
				}
			}
		}

		return count;
	}

	/**
	 * Update the body of this resource.
	 * 
	 * @param ref
	 *        The resource reference.
	 * @param bodyStr
	 */
	protected void updateResource(String ref, String bodyStr)
	{
		// bypass security when reading the resource to copy
		SecurityService.pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});

		try
		{
			// get an id from the reference string
			Reference reference = this.entityManager.newReference(ref);
			String id = reference.getId();
			if (id.startsWith("/content/"))
			{
				id = id.substring("/content".length());
			}

			byte[] body = bodyStr.getBytes("UTF-8");

			ContentResourceEdit edit = this.contentHostingService.editResource(id);
			edit.setContent(body);
			this.contentHostingService.commitResource(edit, 0);
		}
		catch (UnsupportedEncodingException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (IdUnusedException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (TypeException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (InUseException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		catch (OverQuotaException e)
		{
			M_log.warn("translateHtmlBody: " + e.toString());
		}
		finally
		{
			SecurityService.popAdvisor();
		}
	}
}
