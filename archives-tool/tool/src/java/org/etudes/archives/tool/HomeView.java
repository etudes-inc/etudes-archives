/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-tool/tool/src/java/org/etudes/archives/tool/HomeView.java $
 * $Id: HomeView.java 8298 2014-06-22 22:58:38Z ggolden $
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

package org.etudes.archives.tool;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.ambrosia.api.Context;
import org.etudes.ambrosia.api.Value;
import org.etudes.ambrosia.util.ControllerImpl;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Subset;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.util.Web;

/**
 * The /home view for the archives manager tool.
 */
public class HomeView extends ControllerImpl
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(HomeView.class);

	/** The archives service. */
	protected ArchivesService archivesService = null;

	/** Process pages of sites from terms of this size. */
	protected Integer pageSize = Integer.valueOf(100000);

	/** Dependency: SecurityService. */
	protected SecurityService securityService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void get(HttpServletRequest req, HttpServletResponse res, Context context, String[] params)
	{
		// if not logged in as the super user, we won't do anything
		if (!securityService.isSuperUser())
		{
			throw new IllegalArgumentException();
		}

		// no parameters expected
		if (params.length != 2)
		{
			throw new IllegalArgumentException();
		}

		// data for the form values
		Value archiveTermValue = this.uiService.newValue();
		context.put("archiveTermValue", archiveTermValue);
		Value archiveSiteValue = this.uiService.newValue();
		context.put("archiveSiteValue", archiveSiteValue);
		Value listValue = this.uiService.newValue();
		context.put("listValue", listValue);
		Value purgeTermValue = this.uiService.newValue();
		context.put("purgeTermValue", purgeTermValue);
		Value purgeSiteValue = this.uiService.newValue();
		context.put("purgeSiteValue", purgeSiteValue);
		Value xrefSiteValue = this.uiService.newValue();
		context.put("xrefSiteValue", xrefSiteValue);
		Value xrefTermValue = this.uiService.newValue();
		context.put("xrefTermValue", xrefTermValue);
		Value importArchiveValue = this.uiService.newValue();
		context.put("importArchiveValue", importArchiveValue);
		Value importSiteValue = this.uiService.newValue();
		context.put("importSiteValue", importSiteValue);
		Value importSiteUserValue = this.uiService.newValue();
		context.put("importSiteUserValue", importSiteUserValue);
		Subset archiveTermSubset = new Subset(0, this.pageSize);
		context.put("archiveTermSubset", archiveTermSubset);
		Subset purgeTermSubset = new Subset(0, this.pageSize);
		context.put("purgeTermSubset", purgeTermSubset);
		Subset xrefTermSubset = new Subset(0, this.pageSize);
		context.put("xrefTermSubset", xrefTermSubset);
		Subset subset = new Subset(0, this.pageSize);
		context.put("subset", subset);
		Value deleteTermValue = this.uiService.newValue();
		context.put("deleteTermValue", deleteTermValue);
		Value deleteSiteValue = this.uiService.newValue();
		context.put("deleteSiteValue", deleteSiteValue);
		Subset deleteTermSubset = new Subset(0, this.pageSize);
		context.put("deleteTermSubset", deleteTermSubset);
		Value purgeUserValue = this.uiService.newValue();
		context.put("purgeUserValue", purgeUserValue);
		Subset purgeInactiveUsersSubset = new Subset(0, this.pageSize);
		context.put("purgeInactiveUsersSubset", purgeInactiveUsersSubset);
		Subset listInactiveUsersSubset = new Subset(0, this.pageSize);
		context.put("listInactiveUsersSubset", listInactiveUsersSubset);
		Value institutionCodeValue = this.uiService.newValue();
		context.put("institutionCodeValue", institutionCodeValue);

		// render
		uiService.render(ui, context);
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		super.init();

		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void post(HttpServletRequest req, HttpServletResponse res, Context context, String[] params) throws IOException
	{
		if (!context.getPostExpected())
		{
			throw new IllegalArgumentException();
		}

		// no parameters expected
		if (params.length != 2)
		{
			throw new IllegalArgumentException();
		}

		// data for the form values
		Value archiveTermValue = this.uiService.newValue();
		context.put("archiveTermValue", archiveTermValue);
		Value archiveSiteValue = this.uiService.newValue();
		context.put("archiveSiteValue", archiveSiteValue);
		Value listValue = this.uiService.newValue();
		context.put("listValue", listValue);
		Value purgeTermValue = this.uiService.newValue();
		context.put("purgeTermValue", purgeTermValue);
		Value purgeSiteValue = this.uiService.newValue();
		context.put("purgeSiteValue", purgeSiteValue);
		Value xrefSiteValue = this.uiService.newValue();
		context.put("xrefSiteValue", xrefSiteValue);
		Value xrefTermValue = this.uiService.newValue();
		context.put("xrefTermValue", xrefTermValue);
		Value importArchiveValue = this.uiService.newValue();
		context.put("importArchiveValue", importArchiveValue);
		Value importSiteValue = this.uiService.newValue();
		context.put("importSiteValue", importSiteValue);
		Value importSiteUserValue = this.uiService.newValue();
		context.put("importSiteUserValue", importSiteUserValue);
		Subset archiveTermSubset = new Subset(0, this.pageSize);
		context.put("archiveTermSubset", archiveTermSubset);
		Subset purgeTermSubset = new Subset(0, this.pageSize);
		context.put("purgeTermSubset", purgeTermSubset);
		Subset xrefTermSubset = new Subset(0, this.pageSize);
		context.put("xrefTermSubset", xrefTermSubset);
		Subset subset = new Subset(0, this.pageSize);
		context.put("subset", subset);
		Value deleteTermValue = this.uiService.newValue();
		context.put("deleteTermValue", deleteTermValue);
		Value deleteSiteValue = this.uiService.newValue();
		context.put("deleteSiteValue", deleteSiteValue);
		Subset deleteTermSubset = new Subset(0, this.pageSize);
		context.put("deleteTermSubset", deleteTermSubset);
		Value purgeUserValue = this.uiService.newValue();
		context.put("purgeUserValue", purgeUserValue);
		Subset purgeInactiveUsersSubset = new Subset(0, this.pageSize);
		context.put("purgeInactiveUsersSubset", purgeInactiveUsersSubset);
		Subset listInactiveUsersSubset = new Subset(0, this.pageSize);
		context.put("listInactiveUsersSubset", listInactiveUsersSubset);
		Value institutionCodeValue = this.uiService.newValue();
		context.put("institutionCodeValue", institutionCodeValue);

		// read form
		String destination = uiService.decode(req, context);

		// set the page sizes
		archiveTermSubset.setSize(subset.getSize());
		purgeTermSubset.setSize(subset.getSize());
		xrefTermSubset.setSize(subset.getSize());
		deleteTermSubset.setSize(subset.getSize());

		// process based on selection
		if ("ARCHIVE_TERM".equals(destination))
		{
			// start the term archive
			try
			{
				this.archivesService.archiveTerm(archiveTermValue.getValue(), archiveTermSubset, institutionCodeValue.getValue());

				// go to the progress view
				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("ARCHIVE_SITE".equals(destination))
		{
			// start the site archive
			try
			{
				this.archivesService.archiveSite(archiveSiteValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("IMPORT_SITE".equals(destination))
		{
			// start the site import
			try
			{
				this.archivesService.importSite(importArchiveValue.getValue(), importSiteValue.getValue(), importSiteUserValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("LIST".equals(destination))
		{
			// list a site list
			try
			{
				this.archivesService.listArchive(listValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("PURGE_TERM".equals(destination))
		{
			// start the term purge
			try
			{
				this.archivesService.purgeTerm(purgeTermValue.getValue(), purgeTermSubset, institutionCodeValue.getValue());

				// go to the progress view
				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("PURGE_SITE".equals(destination))
		{
			// start the site purge
			try
			{
				this.archivesService.purgeSite(purgeSiteValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("PURGE_USER".equals(destination))
		{
			// start the user purge
			try
			{
				this.archivesService.purgeUser(purgeUserValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("LIST_INACTIVE_USERS".equals(destination))
		{
			// start the inactive users report
			try
			{
				this.archivesService.listInactiveUsers(listInactiveUsersSubset.getSize());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("PURGE_INACTIVE_USERS".equals(destination))
		{
			// start the inactive users purge
			try
			{
				this.archivesService.purgeInactiveUsers(purgeInactiveUsersSubset.getSize());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("DELETE_TERM".equals(destination))
		{
			// start the term delete
			try
			{
				this.archivesService.deleteTermArchive(deleteTermValue.getValue(), deleteTermSubset, institutionCodeValue.getValue());

				// go to the progress view
				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("DELETE_SITE".equals(destination))
		{
			// start the archive delete
			try
			{
				this.archivesService.deleteSiteArchive(deleteSiteValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("XREF_TERM".equals(destination))
		{
			// start the term xref
			try
			{
				this.archivesService.removeXrefTerm(xrefTermValue.getValue(), xrefTermSubset, institutionCodeValue.getValue());

				// go to the progress view
				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		else if ("XREF_SITE".equals(destination))
		{
			// start the site xref
			try
			{
				this.archivesService.removeXrefSite(xrefSiteValue.getValue());

				destination = "/progress";
				res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
				return;
			}
			catch (PermissionException e)
			{
				throw new IllegalArgumentException();
			}
		}

		// otherwise
		destination = "/home";
		res.sendRedirect(res.encodeRedirectURL(Web.returnUrl(req, destination)));
	}

	/**
	 * Set the ArchivesService.
	 * 
	 * @param service
	 *        the ArchivesService.
	 */
	public void setArchivesService(ArchivesService service)
	{
		this.archivesService = service;
	}

	/**
	 * Set the the page size for the term processing.
	 * 
	 * @param size
	 *        The the page size for the term processing - integer.
	 */
	public void setPageSize(String size)
	{
		try
		{
			this.pageSize = Integer.valueOf(size);
		}
		catch (NumberFormatException e)
		{
		}
	}

	/**
	 * Set the security service.
	 * 
	 * @param service
	 *        The security service.
	 */
	public void setSecurityService(SecurityService service)
	{
		this.securityService = service;
	}
}
