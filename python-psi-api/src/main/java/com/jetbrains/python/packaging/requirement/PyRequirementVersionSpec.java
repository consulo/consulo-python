/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.requirement;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

import javax.annotation.Nonnull;
import com.intellij.openapi.util.Pair;

public class PyRequirementVersionSpec
{

	@Nonnull
	private final PyRequirementRelation myRelation;

	@Nonnull
	private final String myVersion;

	public PyRequirementVersionSpec(@Nonnull PyRequirementRelation relation, @Nonnull String version)
	{
		myRelation = relation;
		myVersion = version;
	}

	@Override
	public String toString()
	{
		return myRelation + myVersion;
	}

	@Override
	public boolean equals(Object o)
	{
		if(o == this)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		final PyRequirementVersionSpec spec = (PyRequirementVersionSpec) o;
		return myRelation == spec.myRelation && myVersion.equals(spec.myVersion);
	}

	@Override
	public int hashCode()
	{
		return 31 * myRelation.hashCode() + myVersion.hashCode();
	}

	@Nonnull
	public PyRequirementRelation getRelation()
	{
		return myRelation;
	}

	@Nonnull
	public String getVersion()
	{
		return myVersion;
	}

	public boolean matches(@Nonnull String version)
	{
		switch(myRelation)
		{
			case LT:
				return VERSION_COMPARATOR.compare(version, myVersion) < 0;
			case LTE:
				return VERSION_COMPARATOR.compare(version, myVersion) <= 0;
			case GT:
				return VERSION_COMPARATOR.compare(version, myVersion) > 0;
			case GTE:
				return VERSION_COMPARATOR.compare(version, myVersion) >= 0;
			case EQ:
				final Pair<String, String> publicAndLocalVersions = splitIntoPublicAndLocalVersions(myVersion);
				final Pair<String, String> otherPublicAndLocalVersions = splitIntoPublicAndLocalVersions(version);
				final boolean publicVersionsAreSame = VERSION_COMPARATOR.compare(otherPublicAndLocalVersions.first, publicAndLocalVersions.first) == 0;

				return publicVersionsAreSame && (publicAndLocalVersions.second.isEmpty() || otherPublicAndLocalVersions.second.equals(publicAndLocalVersions.second));
			case NE:
				return VERSION_COMPARATOR.compare(version, myVersion) != 0;
			case COMPATIBLE:
				return false; // TODO: implement matching version against compatible relation
			case STR_EQ:
				return version.equals(myVersion);
			default:
				return false;
		}
	}

	private static Pair<String, String> splitIntoPublicAndLocalVersions(@Nonnull String version)
	{
		final String[] publicAndLocalVersions = version.split("\\+", 2);

		final String publicVersion = publicAndLocalVersions[0];
		final String localVersion = publicAndLocalVersions.length == 1 ? "" : publicAndLocalVersions[1];

		return Pair.createNonNull(publicVersion, localVersion);
	}
}
