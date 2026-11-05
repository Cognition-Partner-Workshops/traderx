extension radius

// Parameters
param application string

// Kubernetes hardening applied as a PodSpec strategic-merge patch (runtimes.kubernetes.pod):
// non-root, no privilege escalation, read-only root filesystem with emptyDir scratch space,
// all capabilities dropped and the RuntimeDefault seccomp profile.
func hardenedRuntime(containerName string, writableDirs string[]) object => {
  kubernetes: {
    pod: {
      securityContext: {
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
        seccompProfile: {
          type: 'RuntimeDefault'
        }
      }
      volumes: map(concat(['/tmp'], writableDirs), (dir, i) => {
        name: 'scratch-${i}'
        emptyDir: {}
      })
      containers: [
        {
          name: containerName
          securityContext: {
            allowPrivilegeEscalation: false
            privileged: false
            readOnlyRootFilesystem: true
            capabilities: {
              drop: [
                'ALL'
              ]
            }
          }
          volumeMounts: map(concat(['/tmp'], writableDirs), (dir, i) => {
            name: 'scratch-${i}'
            mountPath: dir
          })
        }
      ]
    }
  }
}

resource database 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'database'
  properties: {
    application: application
    runtimes: hardenedRuntime('database', ['/database/_data'])
    container: {
      image: 'ghcr.io/finos/traderx/database:latest'
      ports: {
        tcp: {
          containerPort: 18082
        }
        pg:{
          containerPort: 18083
        }
        web: {
          containerPort: 18084
        }
      }
    }
  }
}

resource referencedata 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'reference-data'
  properties: {
    application: application
    runtimes: hardenedRuntime('reference-data', [])
    container: {
      image: 'ghcr.io/finos/traderx/reference-data:latest'
      ports: {
        web: {
          containerPort: 18085
        }
      }
    }
  }
}

resource tradefeed 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'trade-feed'
  properties: {
    application: application
    runtimes: hardenedRuntime('trade-feed', [])
    container: {
      image: 'ghcr.io/finos/traderx/trade-feed:latest'
      ports: {
        web: {
          containerPort: 18086
        }
      }
    }
  }
}

resource peopleservice 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'people-service'
  properties: {
    application: application
    runtimes: hardenedRuntime('people-service', [])
    container: {
      image: 'ghcr.io/finos/traderx/people-service:latest'
      ports: {
        web: {
          containerPort: 18089
        }
      }
    }
  }
}

resource accountservice 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'account-service'
  properties: {
    application: application
    runtimes: hardenedRuntime('account-service', [])
    container: {
      image: 'ghcr.io/finos/traderx/account-service:latest'
      ports: {
        web: {
          containerPort: 18088
        }
      }
      env: {
        DATABASE_TCP_HOST: {
          value: database.name
        }
        PEOPLE_SERVICE_HOST: {
          value: peopleservice.name
        }
      }
    }
    connections: {
      peopleservice: {
        source: peopleservice.id
      }
      db: {
        source: database.id
      }
    }
  }
}

resource positionservice 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'position-service'
  properties: {
    application: application
    runtimes: hardenedRuntime('position-service', [])
    container: {
      image: 'ghcr.io/finos/traderx/position-service:latest'
      ports: {
        web: {
          containerPort: 18090
        }
      }
      env: {
        DATABASE_TCP_HOST: {
          value: database.name
        }
      }
    }
    connections: {
      db: {
        source: database.id
      }
    }
  }
}

resource tradeservice 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'trade-service'
  properties: {
    application: application
    runtimes: hardenedRuntime('trade-service', [])
    container: {
      image: 'ghcr.io/finos/traderx/trade-service:latest'
      ports: {
        web: {
          containerPort: 18092
        }
      }
      env: {
        DATABASE_TCP_HOST: {
          value: database.name
        }
        PEOPLE_SERVICE_HOST: {
          value: peopleservice.name
        }
        ACCOUNT_SERVICE_HOST: {
          value: accountservice.name
        }
        REFERENCE_DATA_HOST: {
          value: referencedata.name
        }
        TRADE_FEED_HOST: {
          value: tradefeed.name
        }
      }
    }
    connections: {
      db: {
        source: database.id
      }
      peopleservice: {
        source: peopleservice.id
      }
      accountservice: {
        source: accountservice.id
      }
      referencedata: {
        source: referencedata.id
      }
      tradefeed: {
        source: tradefeed.id
      }
    }
  }
}

resource tradeprocessor 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'trade-processor'
  properties: {
    application: application
    runtimes: hardenedRuntime('trade-processor', [])
    container: {
      image: 'ghcr.io/finos/traderx/trade-processor:latest'
      ports: {
        web: {
          containerPort: 18091
        }
      }
      env: {
        DATABASE_TCP_HOST: {
          value: database.name
        }
        TRADE_FEED_HOST: {
          value: tradefeed.name
        }
      }
    }
    connections: {
      db: {
        source: database.id
      }
      tradefeed: {
        source: tradefeed.id
      }
    }
  }
}

resource webfrontend 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'web-front-end-angular'
  properties: {
    application: application
    runtimes: hardenedRuntime('web-front-end-angular', ['/app/.angular'])
    container: {
      image: 'ghcr.io/finos/traderx/web-front-end-angular:latest'
      ports: {
        web: {
          containerPort: 18093
        }
      }
      env: {
        HOME: {
          value: '/tmp'
        }
        npm_config_cache: {
          value: '/tmp/.npm'
        }
        DATABASE_TCP_HOST: {
          value: database.name
        }
      }
    }
    connections: {
      db: {
        source: database.id
      }
      tradefeed: {
        source: tradefeed.id
      }
    }
  }
}

resource ingress 'Applications.Core/containers@2023-10-01-preview' = {
  name: 'ingress'
  properties: {
    application: application
    container: {
      image: 'ghcr.io/finos/traderx/ingress:latest'
      ports: {
        web: {
          containerPort: 8080
        }
      }
      env: {
        DATABASE_TCP_HOST: {
          value: database.name
        }
      }
    }
    connections: {
      db: {
        source: database.id
      }
      tradefeed: {
        source: tradefeed.id
      }
      peopleservice: {
        source: peopleservice.id
      }
      accountservice: {
        source: accountservice.id
      }
      positionservice: {
        source: positionservice.id
      }
      tradeservice: {
        source: tradeservice.id
      }
      tradeprocessor: {
        source: tradeprocessor.id
      }
      webfrontend: {
        source: webfrontend.id
      }
    }
  }
}
