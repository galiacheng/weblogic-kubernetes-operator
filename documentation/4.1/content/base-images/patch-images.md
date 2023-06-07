---
title: "Patch running domains"
date: 2019-02-23T16:45:55-05:00
weight: 3
description: "Dynamically update WebLogic images in a running domain."
---

### Apply patched images to a running domain

When updating the WebLogic binaries of a running domain in Kubernetes with a patched container image,
the operator applies the update in a zero downtime fashion.
The procedure for the operator to update the running domain differs depending on the
[domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).
See the following corresponding sections:

- [Domain in PV](#domain-in-pv)
- [Model in Image with auxiliary images](#model-in-image-with-auxiliary-images)
- [Model in Image without auxiliary images](#model-in-image-without-auxiliary-images)
- [Domain in Image](#domain-in-image)

For a broader description of managing the evolution and mutation
of container images to run WebLogic Server in Kubernetes,
see [CI/CD]({{< relref "/managing-domains/cicd/_index.md" >}}).

#### Domain in PV

{{% notice warning %}}
Oracle strongly recommends strictly limiting access to Domain in PV domain home files.
A WebLogic domain home has sensitive information
including credentials that are used to access external resources
(for example, a data source password),
and decryption keys
(for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` domain secret file).
{{% /notice %}}

For Domain in PV domains,
the container image contains only the JDK and WebLogic Server binaries,
and its domain home is located in a Persistent Volume (PV)
where the domain home is generated by the user.

For this domain home source type, you can create your own patched images using the steps
in [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})
or you can obtain patched images from the Oracle Container Registry,
see [Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}).

To apply the patched image,
edit the Domain Resource image reference with the new image name/tag
(for example, `oracle/weblogic:12.2.1.4-patched`).
Then, the operator automatically performs a
[rolling restart]({{< relref "/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.
For more information on server restarts,
see [Restarting]({{< relref "/managing-domains/domain-lifecycle/restarting.md" >}}).

#### Model in Image with auxiliary images

For Model in Image domains when using auxiliary images:

- The container image contains only the JDK and WebLogic Server binaries.
- The [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation and model files
  are located in a separate auxiliary image.
- The domain home is generated by the operator during runtime.

To create and apply patched WebLogic Server images to a running domain of this type,
first follow the steps in
[Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}) or
[Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})
to obtain or create the container image,
and then edit the Domain Resource `image` field with the new image name (for example, `oracle/weblogic:12.2.1.4-patched`).

To apply patched images to a running domain of this type,
follow the same steps that you used to create your original auxiliary image
and alter your domain resource to reference the new image
(see [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}})).
The operator will then perform a [rolling restart]({{< relref "/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.

#### Model in Image without auxiliary images

For Model in Image domains _without_ using auxiliary images:

- The container image contains the JDK, WebLogic Server binaries,
  a [WebLogic Deployment Tooling](https://oracle.github.io/weblogic-deploy-tooling/) (WDT) installation and model files.
- The domain home is generated by the operator during runtime.

If you need to update the image for a running Model in Image domain,
then simply follow the same steps that you used to create the original
image as described in [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}}),
and edit the domain resource's `domain.spec.image` attribute
with the new image's name/tag (`mydomain:v2`).
The operator will then perform a [rolling restart]({{< relref "/managing-domains/domain-lifecycle/restarting#overview" >}})
of the WebLogic domain to update the Oracle Home of the servers.

#### Domain in Image

**NOTE**: The Domain in Image [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain in PV or Model in Image, depending on your needs.

If you need to update the image for a running Domain in Image domain,
then use the WIT [`rebase`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/)
command to update the Oracle Home
for an existing domain image using the patched Oracle Home from a patched container image.
For Domain in Image domains:

- The container image contains the JDK, WebLogic Server binaries, and domain home.

- The domain home is generated during image creation using either WLST or WDT,
  usually with the assistance of the WebLogic Image Tool (WIT).

The `rebase` command does the following:

- Minimizes the image size. The alternative `update` command does _not_ remove old WebLogic installations
  in the image but instead, layers new WebLogic installations on top of the original installation, thereby
  greatly increasing the image size; we strongly recommend _against_ using the `update` command in this situation.

- Creates a new WebLogic image by copying an existing WebLogic domain home
  from an existing image to a new image.
  It finds the domain home location within the original image
  using the image's internal `DOMAIN_HOME` environment variable.

- Maintains the same security configuration
  as the original image because the domain home is copied
  (for example, the `DOMAIN_HOME/security/SerializedSystemIni.dat` file).
  This ensures that pods that are based on the new image
  are capable of joining an already running
  domain with pods on an older version of the image with same security configuration.

Using `rebase`, the new image can be created in one of two ways:

- As a new WebLogic image from a base OS image (similar to the `create` command; recommended).

  **NOTE**:  Oracle strongly recommends rebasing your images with the latest security patches by applying
  the [`--recommendedPatches`](https://oracle.github.io/weblogic-image-tool/userguide/tools/rebase-image/) option.

  To activate:
  - Set `--tag` to the name of the final new image.
  - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
  - Set additional fields (such as the WebLogic and JDK locations),
    similar to those used by `create`.
    See [Create a custom base image]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}).
  - Do _not_ set `--targetImage`.  (When
    you don't specify a `--targetImage`, `rebase` will use
    the same options and defaults as `create`.)

- Or, as a base image, use WebLogic Server CPU images from OCR that do not already have a domain home.

  - Usage:
    - Set `--tag` to the name of the final new image.
    - Set `--sourceImage` to the WebLogic image that contains the WebLogic configuration.
    - Set `--targetImage` to the image that you will you use as a base for the new layer.
  - Example:
    First, generate the new image:
    ```shell
    $ /tmp/imagetool/bin/imagetool.sh rebase \
      --tag mydomain:v2 \
      --sourceImage mydomain:v1 \
      --targetImage container-registry.oracle.com/middleware/weblogic_cpu:12.2.1.4-generic-jdk8-ol8
    ```
   - Second, edit the domain resource `domain.spec.image`
    attribute with the new image's name `mydomain:v2`.
    - Then, the operator automatically performs a
    [rolling upgrade]({{< relref "/managing-domains/domain-lifecycle/restarting#overview" >}})
    on the domain.

In summary, the `rebase` command preserves the original domain home's security configuration
files in a Domain in Image image so that, when they are both deployed to the same running domain,
your updated images and original images can interoperate without a
[domain secret mismatch]({{< relref "/faq/domain-secret-mismatch.md" >}}).

**Notes:**

  - You cannot use the `rebase` command alone to update the domain home configuration.
    If you need to update the domain home configuration,
    then use the `rebase` command first, followed by the `update` command.

  - An Oracle Home and the JDK must be installed in the same directories on each image.