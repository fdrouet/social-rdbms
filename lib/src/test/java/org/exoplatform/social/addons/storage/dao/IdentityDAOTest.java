/*
 * Copyright (C) 2015 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.social.addons.storage.dao;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.addons.storage.entity.IdentityEntity;
import org.exoplatform.social.addons.test.BaseCoreTest;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:tuyennt@exoplatform.com">Tuyen Nguyen The</a>.
 */
public class IdentityDAOTest extends BaseCoreTest {
  private final Log LOG = ExoLogger.getLogger(IdentityDAOTest.class);

  private IdentityDAO identityDAO;

  private List<IdentityEntity> needTobeDelete = new ArrayList<IdentityEntity>();

  public void setUp() throws Exception {
    super.setUp();
    identityDAO = getService(IdentityDAO.class);

    assertNotNull("IdentityDAO must not be null", identityDAO);
    needTobeDelete = new ArrayList<IdentityEntity>();
  }

  @Override
  public void tearDown() throws Exception {
    for (IdentityEntity e : needTobeDelete) {
      identityDAO.delete(e);
    }

    super.tearDown();
  }

  public void testSaveNewIdentity() {
    IdentityEntity entity = new IdentityEntity();
    entity.setProviderId(OrganizationIdentityProvider.NAME);
    entity.setRemoteId("root");
    entity.setEnable(true);

    identityDAO.create(entity);

    IdentityEntity e = identityDAO.find(entity.getId());

    assertNotNull(e);
    assertEquals("root", e.getRemoteId());
    assertEquals(OrganizationIdentityProvider.NAME, e.getProviderId());

    needTobeDelete.add(e);
  }
}
