/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-tool/tool/src/java/org/etudes/archives/tool/ListView.java $
 * $Id: ListView.java 2823 2012-04-03 20:57:39Z ggolden $
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

package org.etudes.archives.tool;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.ambrosia.api.Context;
import org.etudes.ambrosia.api.Value;
import org.etudes.ambrosia.util.ControllerImpl;
import org.etudes.archives.api.ArchiveDescription;
import org.etudes.archives.api.ArchivesService;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.Web;

/**
 * The /list view for the archives user tool.
 */
public class ListView extends ControllerImpl
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ListView.class);

	/** The archives service. */
	protected static ArchivesService archivesService = null;

	/** Dependency: SecurityService. */
	protected SecurityService securityService = null;

	/** Dependency: SessionManager */
	protected SessionManager sessionManager = null;

	/** Dependency: SessionManager */
	protected UserDirectoryService userDirectoryService = null;

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
		// one optional user parameter - admins only
		String userId = sessionManager.getCurrentSessionUserId();
		if (securityService.isSuperUser())
		{
			if ((params.length != 2) && (params.length != 3))
			{
				throw new IllegalArgumentException();
			}

			if (params.length == 3)
			{
				userId = params[2];
			}

			context.put("admin", Boolean.TRUE);
		}

		// no parameters for normal users
		else
		{
			if (params.length != 2)
			{
				throw new IllegalArgumentException();
			}
		}

		// get user information
		User user = null;
		try
		{
			user = this.userDirectoryService.getUser(userId);
		}
		catch (UserNotDefinedException e)
		{
			// try as an eid
			try
			{
				user = this.userDirectoryService.getUserByEid(userId);
				userId = user.getId();
			}
			catch (UserNotDefinedException e1)
			{
				try
				{
					user = this.userDirectoryService.getUser(sessionManager.getCurrentSessionUserId());
				}
				catch (UserNotDefinedException e2)
				{
					throw new IllegalArgumentException();
				}
			}
		}

		// user information
		context.put("user", user);

		// get the Archives for the user
		List<ArchiveDescription> archives = this.archivesService.getUserArchives(user.getId());
		context.put("archives", archives);

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

		// user selection is only an admin feature
		if (!securityService.isSuperUser())
		{
			throw new IllegalArgumentException();
		}

		// to pick up the user id
		Value userIdValue = this.uiService.newValue();
		context.put("userIdValue", userIdValue);

		// read form
		String destination = uiService.decode(req, context);

		destination = "/list/" + userIdValue.getValue();

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
	 * Set the security service.
	 * 
	 * @param service
	 *        The security service.
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
	 * Dependency: UserDirectoryService.
	 * 
	 * @param service
	 *        The UserDirectoryService.
	 */
	public void setUserDirectoryService(UserDirectoryService service)
	{
		this.userDirectoryService = service;
	}
}
