import fileinput
import os

import datetime


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

    next_version = None
    release_props_path = os.path.join(root_dir, 'release.properties')
    with open(release_props_path, 'r') as f:
        for line in f:
            # print("line: {}".format(line))
            if line.startswith("project.rel.com.atlassian.braid\:graphql-braid"):
                _, next_version = line.split('=')

    if next_version:
        cl = Changelog(os.path.join(root_dir, 'CHANGES.md'))
        cl.release(next_version.strip())
    else:
        print("Couldn't find the next version")
        exit(1)
