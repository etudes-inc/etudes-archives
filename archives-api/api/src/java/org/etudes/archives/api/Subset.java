/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/Subset.java $
 * $Id: Subset.java 2823 2012-04-03 20:57:39Z ggolden $
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

package org.etudes.archives.api;

/**
 * Specifies a subset of data by page position and size.
 */
public class Subset
{
	/** The page number (0 is the first page). */
	protected Integer pageNumber = Integer.valueOf(0);

	/** The page size (number of items per page). */
	protected Integer pageSize = Integer.valueOf(0);

	/**
	 * Construct, specifying the page number and page size.
	 * 
	 * @param page
	 *        The page number (0 is the first page)
	 * @param size
	 *        The page size (number of items per page).
	 */
	public Subset(Integer page, Integer size)
	{
		setPage(page);
		setSize(size);
	}

	/**
	 * Access the number of items to skip before arriving at the first item in the page.
	 * 
	 * @return The number of items to skip before arriving at the first item in the page.
	 */
	public Integer getOffset()
	{
		return Integer.valueOf(this.pageNumber.intValue() * this.pageSize.intValue());
	}

	/**
	 * Access the page number.
	 * 
	 * @return The page number.
	 */
	public Integer getPage()
	{
		return this.pageNumber;
	}

	/**
	 * Access the page size.
	 * 
	 * @return The number of items per page.
	 */
	public Integer getSize()
	{
		return this.pageSize;
	}

	/**
	 * Set the page number.
	 * 
	 * @param page
	 *        The page number.
	 */
	public void setPage(Integer page)
	{
		this.pageNumber = page;
	}

	/**
	 * Set the page size.
	 * 
	 * @param size
	 *        The number of items per page.
	 */
	public void setSize(Integer size)
	{
		this.pageSize = size;
	}
}
