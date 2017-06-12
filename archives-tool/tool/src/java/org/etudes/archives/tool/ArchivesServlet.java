/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-tool/tool/src/java/org/etudes/archives/tool/ArchivesServlet.java $
 * $Id: ArchivesServlet.java 2823 2012-04-03 20:57:39Z ggolden $
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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.ambrosia.util.AmbrosiaServlet;
import org.etudes.archives.api.ArchivesService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.tool.api.ToolManager;

/**
 * The Archives servlet; extending AmbrosiaServlet for an activity based default view.
 */
public class ArchivesServlet extends AmbrosiaServlet
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchivesServlet.class);

	/** Archives service. */
	protected ArchivesService archivesService = null;

	/** tool manager reference. */
	protected ToolManager toolManager = null;

	/**
	 * Access the Servlet's information display.
	 * 
	 * @return servlet information.
	 */
	public String getServletInfo()
	{
		return "Archives";
	}

	/**
	 * Initialize the servlet.
	 * 
	 * @param config
	 *        The servlet config.
	 * @throws ServletException
	 */
	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);

		// self-inject
		this.archivesService = (ArchivesService) ComponentManager.get(ArchivesService.class);
		this.toolManager = (ToolManager) ComponentManager.get(ToolManager.class);

		M_log.info("init()");
	}

	/**
	 * Get the default view.
	 * 
	 * @return The default view.
	 */
	protected String getDefaultView()
	{
		// if there's a task running
		if (this.archivesService.isTaskRunning())
		{
			return "progress";
		}

		// otherwise use the default view
		return this.defaultView;
	}
}
