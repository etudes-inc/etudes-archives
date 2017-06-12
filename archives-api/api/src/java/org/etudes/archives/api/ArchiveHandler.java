/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/ArchiveHandler.java $
 * $Id: ArchiveHandler.java 2861 2012-04-24 17:00:08Z ggolden $
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

package org.etudes.archives.api;

/**
 * ArchiveHandler handles archiving data for a specialized set of data.
 */
public interface ArchiveHandler
{
	/**
	 * Archive the data for this site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param archive
	 *        The archive to place the data into.
	 */
	void archive(String siteId, Archive archive);

	/**
	 * @return The application id of the application this handler archives data for.
	 */
	String getApplicationId();
}
