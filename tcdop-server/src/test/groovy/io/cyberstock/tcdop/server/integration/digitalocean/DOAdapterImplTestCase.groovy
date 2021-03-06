/* The MIT License
 *
 * Copyright (c) 2010-2015 Danila A. (atmakin.dv@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
package io.cyberstock.tcdop.server.integration.digitalocean

import com.myjeeva.digitalocean.DigitalOcean
import com.myjeeva.digitalocean.common.ActionStatus
import com.myjeeva.digitalocean.common.DropletStatus
import com.myjeeva.digitalocean.pojo.Account
import com.myjeeva.digitalocean.pojo.Action
import com.myjeeva.digitalocean.pojo.Delete
import com.myjeeva.digitalocean.pojo.Droplet
import com.myjeeva.digitalocean.pojo.Droplets
import com.myjeeva.digitalocean.pojo.Image
import com.myjeeva.digitalocean.pojo.Images
import com.myjeeva.digitalocean.pojo.Network
import com.myjeeva.digitalocean.pojo.Networks
import io.cyberstock.tcdop.model.DOConfigConstants
import io.cyberstock.tcdop.model.DOSettings
import io.cyberstock.tcdop.model.WebConstants
import io.cyberstock.tcdop.model.error.DOError
import io.cyberstock.tcdop.server.integration.digitalocean.adapter.DOAdapter
import io.cyberstock.tcdop.server.integration.digitalocean.adapter.impl.DOAdapterImpl
import io.cyberstock.tcdop.server.integration.digitalocean.adapter.impl.DOAdapterUtils
import io.cyberstock.tcdop.server.integration.teamcity.DOCloudImage
import io.cyberstock.tcdop.server.integration.teamcity.web.DOSettingsUtils
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertNotNull
import static org.testng.Assert.assertTrue;

/**
 * Created by beolnix on 08/08/15.
 */
class DOAdapterImplTestCase {



    def PREFIX = "prefix"


    @Test
    public void createInstanceTest() {
        DOSettings settings = DOSettingsUtils.convertToDOSettings(getParametersMap())
        def IMAGE_ID = 123
        def REGION_SLUG = "test"

        def client = [createDroplet: { droplet ->
            return droplet
        }] as DigitalOcean

        DOCloudImage cloudImage = new DOCloudImage(
                new Image(id: IMAGE_ID,
                        name: "test",
                        minDiskSize: settings.getSize().diskSize,
                        slug: "deb",
                        regions: [REGION_SLUG]
                        )
                )

        DOAdapter doAdapter = new DOAdapterImpl(client, -1, 1L)

        Droplet droplet = doAdapter.createInstance(settings, cloudImage)

        assertNotNull(droplet)
        assertTrue(droplet.name.startsWith(PREFIX))
        assertEquals(droplet.size, settings.getSize().slug)
        assertEquals(droplet.image.id, IMAGE_ID)
        assertEquals(droplet.region.slug, REGION_SLUG)
    }

    @Test
    public void getAccountTest() {
        def getAccountInfoExecuted = false
        def client = [getAccountInfo: {
            getAccountInfoExecuted = true
            return new Account()
        }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, -1, 1L)

        Account account = doAdapter.checkAccount()

        assertTrue(getAccountInfoExecuted)
        assertNotNull(account)
    }

    @Test
    public void waitForDropletInitializationTestPositive() {
        def client = [getDropletInfo: {
            def droplet = new Droplet()
            droplet.status = DropletStatus.ACTIVE

            def networks = new Networks()
            networks.version4Networks = [new Network(ipAddress: "127.0.0.1")]
            droplet.networks = networks

            return droplet
        }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, -1, 1L)

        String ipv4 = doAdapter.waitForDropletInitialization(123)

        assertNotNull(ipv4)
    }

    @Test(expectedExceptions = [DOError])
    public void waitForDropletInitializationTestPositiveNegative1() {
        def client = [getDropletInfo: {
            def droplet = new Droplet()
            droplet.status = DropletStatus.ARCHIVE

            def networks = new Networks()
            networks.version4Networks = [new Network(ipAddress: "127.0.0.1")]
            droplet.networks = networks

            return droplet
        }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, -1, 1L)

        String ipv4 = doAdapter.waitForDropletInitialization(123)

        assertNotNull(ipv4)
    }

    @Test
    public void waitForActionResultTestPositive() {
        def client = [getActionInfo: { id ->
            def action = new Action(id: id)
            action.completedAt = new Date()
            action.status = ActionStatus.COMPLETED
            return action
        }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        Date endDate = doAdapter.waitForActionResult(new Action(id: 1))
        assertNotNull(endDate)
    }

    @Test
    public void isDropletActiveTest() {

        assertFalse(DOAdapterUtils.isDropletActive(null))

        Droplet droplet = new Droplet(status: DropletStatus.ACTIVE)
        assertTrue(DOAdapterUtils.isDropletActive(droplet))

        Droplet droplet2 = new Droplet(status: DropletStatus.ARCHIVE)
        assertFalse(DOAdapterUtils.isDropletActive(droplet2))

    }

    @Test
    public void terminateInstanceTest() {
        def client = [deleteDroplet: { dropletId ->
            return new Delete(isRequestSuccess: true)
        }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        assertTrue(doAdapter.terminateInstance(1))
    }

    @Test
    public void rebootInstanceTest() {
        def client = [
                rebootDroplet: { dropletId ->
                    return new Action(id: 1, status: ActionStatus.IN_PROGRESS, completedAt: new Date())
                },
                getActionInfo: { actionId ->
                    return new Action(status: ActionStatus.COMPLETED, completedAt: new Date())
                }
        ] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        assertNotNull(doAdapter.restartInstance(1))
    }

    @Test
    public void findImageByNameTestPositive() {
        def client = [
                getUserImages: { pageNumber ->
                    if (pageNumber <= 1) {
                        return new Images(images: [new Image(id: 1, name: "test")])
                    } else {
                        return new Images(images: [])
                    }
                },
                getImageInfo: { id ->
                    return new Image(id: id, name: "test")
                }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        assertTrue(doAdapter.findImageByName("test").isPresent())
    }

    @Test
    public void findImageByNameTestNegative1() {
        def client = [
                getUserImages: { pageNumber ->
                    if (pageNumber <= 1) {
                        return new Images(images: [new Image(id: 1, name: "test")])
                    } else {
                        return new Images(images: [])
                    }
                },
                getImageInfo: { id ->
                    return null
                }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        assertFalse(doAdapter.findImageByName("test").isPresent())
    }

    @Test
    public void findImageByNameTestNegative2() {
        def client = [
                getUserImages: { pageNumber ->
                    if (pageNumber <= 1) {
                        return new Images(images: [new Image(id: 1, name: "ababa")])
                    } else {
                        return new Images(images: [])
                    }
                },
                getImageInfo: { id ->
                    return null
                }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)

        assertFalse(doAdapter.findImageByName("test").isPresent())
    }

    @Test
    public void getDropletsTest() {
        def client = [
                getAvailableDroplets: { pageNumber ->
                    if (pageNumber < 2) {
                        return new Droplets(droplets: [new Droplet()])
                    } else {
                        return new Droplets(droplets: [])
                    }
                }] as DigitalOcean

        DOAdapter doAdapter = new DOAdapterImpl(client, 1, 500L)
        assertEquals(doAdapter.getDroplets().size(), 2)
    }



    def getParametersMap() {
        def params = [:]
        params[WebConstants.DO_INTEGRATION_MODE] = DOConfigConstants.PREPARED_IMAGE_MODE_CODE
        params[WebConstants.INSTANCES_COUNT_LIMIT] = '5'
        params[WebConstants.TOKEN] = 'test'
        params[WebConstants.DROPLET_SIZE] = '32GB'
        params[WebConstants.IMAGE_NAME] = 'name'
        params[WebConstants.DROPLET_NAME_PREFIX] = PREFIX
        params
    }
}
