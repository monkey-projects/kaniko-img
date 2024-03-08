# MonkeyCI Kaniko image

This is a container image that uses the [Kaniko](https://github.com/GoogleContainerTools/kaniko)
debug image as a base.  This exists because at the time of writing, it is not possible to run
`/bin/sh` in the Kaniko image in [OCI container instances](https://docs.oracle.com/en-us/iaas/Content/container-instances/home.htm).
It always fails with a "no permission" error, which has something to do with the user permissions
imposed by OCI.

So currently the only workaround is to create a new image based on Busybox, copy the Kaniko
files into it and run that as a container instance.

# License

[MIT License](LICENSE)