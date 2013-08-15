/*
 *  Copyright 2005 HBA Specto Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
/* Generated by Together */

package com.hbaspecto.functions;


/**
 * This function implements a combination linear and logistic response.
 * <p>in particular
 * <p>y = y0 + delta*(exp(lambda(x-x0))-1)/(1+exp(lambda(x-x0))) + slope*(x-x0)
 * <p> A linear function can be set up by setting delta to zero and slope to nonzero.  A logistic function can be set up by setting detla to nonzero and slope to zero.
 *
 * @author J. Abraham
 */
public class LogisticPlusLinearFunction implements SingleParameterFunction {
    double y0;
    double x0;
    double slope;
    double lambda;
    double delta;

    public LogisticPlusLinearFunction(double y0, double x0, double lambda, double delta, double slope) {
        this.y0 = y0;
        this.x0 = x0;
        this.lambda = lambda;
        this.delta = delta;
        this.slope = slope;
    }

    @Override
    public double evaluate(double x) {
        double temp = Math.exp(lambda * (x - x0));
        if (Double.isInfinite(temp)) {
            return y0+delta+slope*(x-x0);
        }
        return y0 + delta * (temp - 1.0) / (temp + 1.0) + slope * (x - x0);
    }

    @Override
    public double derivative(double x) {
        double temp = Math.exp(lambda * (x - x0));
        if (Double.isInfinite(temp)) {
            return slope;
        }
        return 2.0 * delta * lambda * temp / (1 + temp) / (1 + temp) + slope;
    }

}

;
