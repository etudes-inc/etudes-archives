/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/Archiver.java $
 * $Id: Archiver.java 2929 2012-05-16 21:01:57Z ggolden $
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
 * Archiver handles the data flow and storage of the archiving process.
 */
public interface Archiver
{
	/**
	 * Delete the archive for this site.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	void deleteArchive(String siteId);

	/**
	 * Create a new archive for the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The Archive.
	 */
	Archive newArchive(String siteId);

	/**
	 * Create an archive filled in with the archived data
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The Archive.
	 */
	Archive readArchive(String siteId);
}
