#### Creating the image with WIT

As an alternative to put all the WDT models, variables, and archives including the WDT executables in the main image, you
can also create a separate file image containing the same artifacts and use the Common Mount Option (NEED LINK)

At this point, you have staged all of the files needed for image `model-in-image:WLS-v1`; they include:

- `/tmp/mii-sample/model-images/weblogic-deploy.zip`
- `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.yaml`
- `/tmp/mii-sample/model-images/model-in-image__WLS-v1/model.10.properties`
- `/tmp/mii-sample/model-images/model-in-image__WLS-v1/archive.zip`

If you don't see the `weblogic-deploy.zip` file, then you missed a step in the [prerequisites]({{< relref "/samples/simple/domains/model-in-image/prerequisites.md" >}}).

Run the following commands to create the model file image `model-file-image:v1` and verify that it worked:

  ```shell
  $ utils/build-file-image.sh
  ```
  ```shell