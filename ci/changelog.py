import fileinput
import os

import datetime
from xml.etree import ElementTree as et


def get_release_version(path):
    ns = "http://maven.apache.org/POM/4.0.0"

    version = ""

    tree = et.ElementTree()
    tree.parse(path)

    p = tree.getroot().find("{%s}parent" % ns)

    if p is not None:
        if p.find("{%s}version" % ns) is not None:
            version = p.find("{%s}version" % ns).text

    if tree.getroot().find("{%s}version" % ns) is not None:
        version = tree.getroot().find("{%s}version" % ns).text

    if "-SNAPSHOT" in version:
        version = version[0:-1 * len("-SNAPSHOT")]
    return version


class Changelog(object):

    def __init__(self, path):
        self.path = path

        self.unreleased_version = None
        self._reload()

    def _reload(self):
        if not os.path.isfile(self.path):
            raise Exception("Changelog {} doesn't exist".format(self.path))

    def release(self, release_version):

        f = fileinput.input(self.path, inplace=True)
        try:
            for line in f:
                if "(Unreleased)" in line:
                    print("(Unreleased)")
                    print("-------------------")
                    print("")
                    print("- ")
                    print("")
                    print("{} ({})".format(release_version, datetime.date.today().strftime('%Y-%m-%d')))
                else:
                    print(line),
        finally:
            f.close()


if __name__ == "__main__":
    root_dir = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
    release_version = get_release_version(os.path.join(root_dir, 'pom.xml'))

    if release_version:
        cl = Changelog(os.path.join(root_dir, 'CHANGES.md'))
        cl.release(release_version)
    else:
        print("Couldn't find the release version")
        exit(1)
